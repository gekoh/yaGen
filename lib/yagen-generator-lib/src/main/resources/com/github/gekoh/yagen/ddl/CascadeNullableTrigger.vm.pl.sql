#if( $is_postgres )
create or replace function ${triggerName}()
  returns trigger AS $$
#else
create or replace trigger ${triggerName}
before insert or update of ${fkColumnName} on ${tableName}
for each row
#end
declare
  null_permitted_setting integer:=0;
begin
#if( $bypassFunctionality )
  if is_bypassed(upper('${triggerName}')) = 1 then
    return#if( $is_postgres ) new#{end};
  end if;
#end

  begin
    select BOOLEAN_VALUE into #if($is_postgres)strict #{end}null_permitted_setting
    from $SYSTEM_SETTING where REQUIRED_BY='IMP' and SETTING_KEY='CASCADE_NULLABLE_CTRL.null_permitted';
  exception when others then null;
  end;
  if ${new}.${fkColumnName} is null and null_permitted_setting=0 then
    #if( $is_postgres )perform #{end}raise_application_error(-20001, 'the relation ${tableName}.${fkColumnName} is only nullable during cleanup job if target entity is deleted');
  end if;

#if( $is_postgres )  return new;
#end
end;#if( $is_postgres )

$$ LANGUAGE 'plpgsql';#end
