-- trigger used for redirecting persistence to I18N description table $i18nTblName
create trigger ${triggerName}
instead of #if(${operation} == 'I') insert #elseif (${operation} == 'U') update #else delete #end on $i18nDetailTblName
referencing #if( ${operation} != 'D' ) new as new #end #if( ${operation} != 'I' ) old as old #end
for each row
begin atomic
#if( $bypassFunctionality )
  if not(is_statically_bypassed('${triggerName}')) and is_bypassed(upper('${triggerName}')) = 0 then
#end
#if (${operation} == 'D')
  delete from ${i18nTblName} where ${i18nFKColName}=:OLD.${i18nFKColName} and language_cd=:OLD.language_cd;
#else
  begin atomic
    declare exit handler for sqlstate '23505' --integrity constraint violation: unique constraint or index violation
      update ${i18nTblName} set
#foreach( $column in $columns )
       #if( $column != ${columns[0]} ),#end ${column}=new.${column}
#end
      where ${i18nFKColName}=new.${i18nFKColName} and language_cd=new.language_cd;
    insert into ${i18nTblName} (
#foreach( $column in $columns )
        #if( $column != ${columns[0]} ), #end${column}
#end
      )
      values (
#foreach( $column in $columns )
       #if( $column != ${columns[0]} ),#end new.${column}
#end
      );
  end;
#end
#if( $bypassFunctionality )
  end if;
#end
end;