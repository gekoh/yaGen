create or replace
trigger ${triggerName}
before insert or update on ${liveTableName}
for each row
declare
  user_name ${liveTableName}.${created_by}%type;
begin
#if( $bypassFunctionality )
  if is_bypassed(upper('${triggerName}')) = 1 then
    return#if( $is_postgres ) new#{end};
  end if;

#end
  if INSERTING then
    user_name := case when :new.${created_at} is not null then :new.${created_by} end;
  else #*
-- re-/set the modifier / modifierdate values ONLY if no different modifier since last update
-- has been set in incoming value - this enables entity listeners to work properly as their values will be kept
-- without the need of connection transforming
-- 2013-07-05: changed to only use the modifier from entity listeners as the timestamp has to be provided by DB -> PTA timeshift issues *#
  ##    -- detect change made in application based on the modified timestamp, in this case the app will provide the modifier name within the update statement
    user_name := case when :new.${last_modified_at} is not null and (:old.${last_modified_at} is null or :new.${last_modified_at} <> :old.${last_modified_at} )
                        then :new.${last_modified_by} end;
  end if;

  user_name:= substr(get_audit_user(user_name), 1, ${MODIFIER_COLUMN_NAME_LENGTH});

  if INSERTING then
    :new.${created_at} := systimestamp;
    :new.${created_by} := user_name;
    :new.${last_modified_at} := null;
    :new.${last_modified_by} := null;
  else
##    -- disable update of creation info
    :new.${created_at} := :old.${created_at};
    :new.${created_by} := :old.${created_by};
    :new.${last_modified_by} := user_name;
    :new.${last_modified_at} := systimestamp;
  end if;
end;