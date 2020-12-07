create trigger ${triggerName}
after #if(${operation} == 'I') insert #elseif (${operation} == 'U') update #else delete #end on ${liveTableName}
referencing #if( ${operation} != 'D' ) new as new #end #if( ${operation} != 'I' ) old as old #end
for each row
begin atomic
  declare transaction_id_used bigint;
  declare transaction_timestamp_found ${timestampType};
  declare hst_operation char(1) default '${operation}';
  declare live_rowid varchar(64);
  declare hst_uuid_used varchar(32);
  declare hst_table_name varchar(30);
  declare prev_operation char(1);
  declare affected_rowcount integer;
  declare msg varchar(255);
#if( $MODIFIER_COLUMN_NAME )  declare hst_modified_by ${MODIFIER_COLUMN_TYPE};

  set hst_modified_by=#if(${operation}!='U')substr(get_audit_user(null), 1, ${MODIFIER_COLUMN_NAME_LENGTH})#{else}new.${MODIFIER_COLUMN_NAME}#end;
#end  set transaction_id_used=0;--txid_current();
  set live_rowid=''
    #foreach( $pkColumn in $pkColumns )
        ||#if(${operation}=='D')old.${pkColumn}#{else}new.${pkColumn}#end
    #end
    ;
-- when using oracle syntax in HSQLDB newer versions seem to override user function sys_guid() with built-in version
-- which returns binary data and is incompatible with our triggers, thus we specifically use the self created sys_guid
  set hst_uuid_used=public.sys_guid();
  set hst_table_name=upper('${liveTableName}');
#if( $bypassFunctionality )
  if not(is_statically_bypassed('${triggerName}')) and is_bypassed(upper('${triggerName}')) = 0 then
#end
#if (${operation} == 'U')
--/* TRYING to reactivate detection of changes (WAS: need to disable detection of changes, somehow this causes a NPE within HSQLDB, only god knows why)
  if 1=0
#foreach( $column in $histRelevantCols )
  or ((new.$column is null and old.$column is not null) or
      (new.$column is not null and old.$column is null) or
      new.$column!=old.$column)
#end
  then
#end
    begin atomic
      declare exit handler for not found
        begin atomic
          declare exit handler for sqlexception;
          set transaction_timestamp_found=systimestamp_9();
          insert into HST_CURRENT_TRANSACTION (transaction_id, transaction_timestamp)
            values (transaction_id_used, transaction_timestamp_found);
        end;

      select transaction_timestamp into transaction_timestamp_found
      from HST_CURRENT_TRANSACTION
      where transaction_id=transaction_id_used;
    end;

    begin atomic
      declare exit handler for not found
        begin atomic
          insert into hst_modified_row values (hst_table_name, live_rowid, hst_operation, hst_uuid_used);

#if (${operation} != 'I')
          /* invalidate latest entry in history table */
          update ${hstTableName} h set invalidated_at=transaction_timestamp_found
            where
              transaction_timestamp < transaction_timestamp_found and
              operation <> 'D' and
#foreach( $pkColumn in $pkColumns )
  #if( $!{columnMap.get($pkColumn).nullable} )
              ((${pkColumn} is null and old.${pkColumn} is null) or ${pkColumn}=old.${pkColumn}) and
  #else
              ${pkColumn}=old.${pkColumn} and
  #end
#end
              invalidated_at is null;

          GET DIAGNOSTICS affected_rowcount = ROW_COUNT;
          if affected_rowcount<>1 then
            set msg='unable to invalidate history record for '||hst_table_name
#foreach( $pkColumn in $pkColumns )
                ||' ${pkColumn}='''|| old.${pkColumn} ||''''
#end
              ;
            SIGNAL SQLSTATE '20100' SET MESSAGE_TEXT = msg;
          end if;
#end
        end;

      select operation, hst_uuid into prev_operation, hst_uuid_used
        from hst_modified_row
       where table_name=hst_table_name
         and row_id=live_rowid;

      if prev_operation='I' and hst_operation='U' then
        set hst_operation='I';
      elseif prev_operation='I' and hst_operation='D' then
        set hst_operation=null;
      end if;

      delete from ${hstTableName} where hst_uuid=hst_uuid_used;
    end;

    if hst_operation is not null then
      insert into ${hstTableName} (#foreach( $pkColumn in $pkColumns ) ${pkColumn},#end #foreach( $column in $nonPkColumns ) #if( $column != $histColName ) ${column},#end #end hst_uuid, operation, ${histColName})
      values (#foreach( $pkColumn in $pkColumns ) #if(${operation}=='D')old.${pkColumn}#{else}new.${pkColumn}#{end},#end#foreach( $column in $nonPkColumns )#if( $column == $MODIFIER_COLUMN_NAME ) hst_modified_by#{else}#if(${operation}=='D') null#{else} new.${column}#{end}#end,#end hst_uuid_used, hst_operation, transaction_timestamp_found);
    end if;

#if (${operation} == 'U')
  end if;
#end
#if( $bypassFunctionality )
  end if;
#end
end;