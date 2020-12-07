create or replace
trigger ${triggerName}
before insert or update on ${liveTableName}
for each row
#if( $last_modified_by )
declare
  user_name ${liveTableName}.${last_modified_by}%type;
#end
begin
#if( $bypassFunctionality )
  if is_bypassed(upper('${triggerName}')) = 1 then
    return#if( $is_postgres ) new#{end};
  end if;

#end
#if( $last_modified_by )
#*
-- re-/set the modifier / modifierdate values ONLY if no different modifier since last update
-- has been set in incoming value - this enables entity listeners to work properly as their values will be kept
-- without the need of connection transforming
-- 2013-07-05: changed to only use the modifier from entity listeners as the timestamp has to be provided by DB -> PTA timeshift issues *#
  ##    -- detect change made in application based on the modified timestamp, in this case the app will provide the modifier name within the update statement
  user_name := case when :new.${last_modified_at} is not null and (:old.${last_modified_at} is null or :new.${last_modified_at} <> :old.${last_modified_at} )
                      then :new.${last_modified_by} end;

  :new.${last_modified_by} := substr(get_audit_user(user_name), 1, ${MODIFIER_COLUMN_NAME_LENGTH});
#end
  :new.${last_modified_at} := systimestamp;
end;