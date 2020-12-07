#if( $is_postgres )
create or replace function ${objectName}()
  returns trigger AS $$
#else
create or replace
trigger ${objectName}
after insert or update or delete on ${liveTableName}
for each row
#end
declare
#if($is_postgres)
  sql_rowcount integer;
#end
  transaction_timestamp_found timestamp;
  hst_operation HST_MODIFIED_ROW.operation%TYPE:=#if($is_postgres)substr(TG_OP, 1, 1)#{else}case when inserting then 'I'
                                                      when updating then 'U'
                                                      when deleting then 'D' end#{end};
#if( $is_postgres )
  live_rowid tid:=case when hst_operation in ('U', 'I') then ${new}.ctid else ${old}.ctid end;
#else
  live_rowid rowid:=coalesce(${new}.rowid, ${old}.rowid);
#end
  hst_uuid_used ${hstTableName}.hst_uuid%TYPE:=sys_guid();
#if( $MODIFIER_COLUMN_NAME )  hst_modified_by ${MODIFIER_COLUMN_TYPE}:=substr(get_audit_user(null), 1, ${MODIFIER_COLUMN_NAME_LENGTH});
#end  hst_table_name ${varcharType}:=upper('${liveTableName}');
begin
#if( $bypassFunctionality )
  if is_bypassed(upper('${objectName}')) = 1 then
    return#if( $is_postgres ) new#{end};
  end if;
#end

#if( !$is_postgres )
  if inserting or deleting
#foreach( $column in $histRelevantCols )
  or ((${new}.$column is null and ${old}.$column is not null) or
      (${new}.$column is not null and ${old}.$column is null) or
#if( $is_oracle && $blobCols.contains($column) )
      DBMS_LOB.COMPARE(${new}.$column, ${old}.$column) <> 0)
#else
      ${new}.$column!=${old}.$column)
#end
#end
  then

#end
    begin
      select transaction_timestamp into #if($is_postgres)strict #{end}transaction_timestamp_found
      from HST_CURRENT_TRANSACTION
      where transaction_id=#if($is_postgres)txid_current()#{else}DBMS_TRANSACTION.LOCAL_TRANSACTION_ID#{end};
    exception when no_data_found then
      transaction_timestamp_found:=#if($is_postgres)localtimestamp#{else}systimestamp#{end};
      insert into HST_CURRENT_TRANSACTION (transaction_id, transaction_timestamp)
        values (#if($is_postgres)txid_current()#{else}DBMS_TRANSACTION.LOCAL_TRANSACTION_ID#{end}, transaction_timestamp_found);
    end;

#if( !$is_postgres )
    if ${new}.rowid<>${old}.rowid then
      update hst_modified_row set row_id=${new}.rowid
        where table_name=hst_table_name
          and row_id=${old}.rowid;
    end if;
#else

    if (hst_operation  = 'U') and ${new}.ctid <> ${old}.ctid then
      update hst_modified_row set row_id=${new}.ctid
        where transaction_id=txid_current() and table_name=hst_table_name
          and row_id=${old}.ctid;
    end if;
#end

    begin
      insert into hst_modified_row values (#if($is_postgres)txid_current(), #{end}hst_table_name, live_rowid, hst_operation, hst_uuid_used);

      if hst_operation<>'I' then
        -- invalidate latest entry in history table
        update ${hstTableName} h set invalidated_at=transaction_timestamp_found
          where
            transaction_timestamp < transaction_timestamp_found and
            operation <> 'D' and
#foreach( $pkColumn in $pkColumns )
  #if( $!{columnMap.get($pkColumn).nullable} )
            ((${pkColumn} is null and ${old}.${pkColumn} is null) or ${pkColumn}=${old}.${pkColumn}) and
  #else
            ${pkColumn}=${old}.${pkColumn} and
  #end
#end
            invalidated_at is null;

#if($is_postgres)
        GET DIAGNOSTICS sql_rowcount = ROW_COUNT;
        if sql_rowcount<>1 then
#else
        if sql%rowcount<>1 then
#end
#if( $is_postgres )          perform#{end}
          raise_application_error(-20100, 'unable to invalidate history record for '||hst_table_name
#foreach( $pkColumn in $pkColumns )
              ||' ${pkColumn}='''|| ${old}.${pkColumn} ||''''
#end
            );
        end if;
      end if;
    exception when #if(!$is_postgres)dup_val_on_index#{else}unique_violation#end then
      declare
        prev_operation ${hstTableName}.operation%TYPE;
      begin
        select operation, hst_uuid into #if($is_postgres)strict #{end}prev_operation, hst_uuid_used
          from hst_modified_row
         where #if($is_postgres)transaction_id=txid_current() and #{end}table_name=hst_table_name
           and row_id=live_rowid;

        if prev_operation='I' and hst_operation='U' then
          hst_operation:='I';
        elsif prev_operation='I' and hst_operation='D' then
          hst_operation:=null;
        end if;
      end;

      delete from ${hstTableName} where hst_uuid=hst_uuid_used;

    end;

    if hst_operation is not null then
      if hst_operation<>'D' then
        insert into ${hstTableName} (#foreach( $pkColumn in $pkColumns ) ${pkColumn},#end #foreach( $column in $nonPkColumns ) #if( $column != $histColName ) ${column},#end #end hst_uuid, operation, ${histColName})
        values (#foreach( $pkColumn in $pkColumns ) coalesce(${new}.${pkColumn}, ${old}.${pkColumn}),#end #foreach( $column in $nonPkColumns ) #if( $column == $MODIFIER_COLUMN_NAME ) coalesce(${new}.${column}, hst_modified_by),#else #if( $column != $histColName ) ${new}.${column},#end #end #end hst_uuid_used, hst_operation, transaction_timestamp_found);
      else
        insert into ${hstTableName} (#foreach( $pkColumn in $pkColumns ) ${pkColumn},#end #foreach( $column in $nonPkColumns ) #if( $column != $histColName ) ${column},#end #end hst_uuid, operation, ${histColName})
        values (#foreach( $pkColumn in $pkColumns ) ${old}.${pkColumn},#end #foreach( $column in $nonPkColumns ) #if( $column == $MODIFIER_COLUMN_NAME ) hst_modified_by,#else #if( $column != $histColName )#if( $noNullColumns.contains($column) ) ${old}.$column#else null#end,#end #end #end hst_uuid_used, hst_operation, transaction_timestamp_found);
      end if;
    end if;

#if( $is_postgres )
  return new;
#else
  end if;
#end
end;#if( $is_postgres )

$$ LANGUAGE 'plpgsql';#end