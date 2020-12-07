create trigger ${triggerName}
before #if(${operation} == 'I') insert #elseif (${operation} == 'U') update #else delete #end on ${liveTableName}
referencing #if( ${operation} != 'D' ) new as new #end #if( ${operation} != 'I' ) old as old #end
for each row
begin atomic
  declare user_name varchar(${MODIFIER_COLUMN_NAME_LENGTH});
#if( $bypassFunctionality )
  if not(is_statically_bypassed('${triggerName}')) and is_bypassed(upper('${triggerName}')) = 0 then
#end
#if( ${operation} == 'I' )
    set user_name = case when new.${created_at} is not null then new.${created_by} end;
#end #if( ${operation} == 'U' ) #*
-- re-/set the modifier / modifierdate values ONLY if no different modifier since last update
-- has been set in incoming value - this enables entity listeners to work properly as their values will be kept
-- without the need of connection transforming
-- 2013-07-05: changed to only use the modifier from entity listeners as the timestamp has to be provided by DB -> PTA timeshift issues *#
##    -- detect change made in application based on the modified timestamp, in this case the app will provide the modifier name within the update statement
    set user_name = case when new.${last_modified_at} is not null and (old.${last_modified_at} is null or new.${last_modified_at} <> old.${last_modified_at} )
                         then new.${last_modified_by} end;
#end

    set user_name = substr(get_audit_user(user_name), 1, ${MODIFIER_COLUMN_NAME_LENGTH});

#if( ${operation} == 'I' )
    set new.${created_at} = systimestamp_9();
##    -- take creator if provided by the application within insert statement as first priority
    set new.${created_by} = user_name;
    set new.${last_modified_at} = null;
    set new.${last_modified_by} = null;
#end #if( ${operation} == 'U' )
##    -- disable update of creation info
    set new.${created_at} = old.${created_at};
    set new.${created_by} = old.${created_by};
    set new.${last_modified_at} = systimestamp_9();
    set new.${last_modified_by} = user_name;
#end
#if( $bypassFunctionality )
  end if;
#end
end;