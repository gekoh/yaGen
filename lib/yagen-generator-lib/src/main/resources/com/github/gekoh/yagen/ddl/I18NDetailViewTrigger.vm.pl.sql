#if( $is_postgres )
create function ${objectName}()
returns trigger AS $$
#else
-- trigger used for redirecting persistence to I18N description table $i18nTblName
create or replace trigger $objectName
instead of update or insert or delete
on $i18nDetailTblName
for each row
#end
declare
  i18n_row_found ${i18nTblName}%rowtype;
begin
#if( $bypassFunctionality )
  if is_bypassed(upper('${objectName}')) = 1 then
    return#if( $is_postgres ) new#{end};
  end if;
#end

  if #if( $is_postgres )TG_OP='DELETE'#{else}deleting#end then
    delete from ${i18nTblName} where ${i18nFKColName}=${old}.${i18nFKColName} and language_cd=${old}.language_cd;
    return#if( $is_postgres ) new#end;
  end if;
#foreach( $column in $columns )
  i18n_row_found.${column}:=${new}.${column};
#end
  begin
    insert into ${i18nTblName} values #if( $is_postgres )(i18n_row_found.*)#{else}i18n_row_found#end;
  exception when #if( $is_postgres )unique_violation#{else}dup_val_on_index#end then
    update ${i18nTblName} set #if( !$is_postgres )ROW=i18n_row_found
#else
#foreach( $column in $columns )
       #if( $column != ${columns[0]} ),#end ${column}=i18n_row_found.${column}
#end
#end
     where ${i18nFKColName}=i18n_row_found.${i18nFKColName} and language_cd=i18n_row_found.language_cd;
  end;
#if( $is_postgres )
  return new;
#end
end;#if( $is_postgres )

$$ LANGUAGE 'plpgsql';#end