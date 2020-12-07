create trigger ${triggerName}
before #if(${operation} == 'I') insert #elseif (${operation} == 'U') update #else delete #end on ${tableName}
referencing #if( ${operation} != 'D' ) new as new #end #if( ${operation} != 'I' ) old as old #end
for each row
begin atomic
#if( $bypassFunctionality )
  if not(is_statically_bypassed('${triggerName}')) and is_bypassed(upper('${triggerName}')) = 0 then
#end
  if ${new}.${fkColumnName} is null and coalesce((select BOOLEAN_VALUE from $SYSTEM_SETTING where REQUIRED_BY='IMP' and SETTING_KEY='CASCADE_NULLABLE_CTRL.null_permitted'), 0)=0 then
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'the relation ${tableName}.${fkColumnName} is only nullable during cleanup job if target entity is deleted';
  end if;
#if( $bypassFunctionality )
  end if;
#end
end;