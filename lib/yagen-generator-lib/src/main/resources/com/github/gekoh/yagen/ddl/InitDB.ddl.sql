#if( $is_oracle )
------- CreateDDL statement separator -------
create or replace function get_audit_user(client_user_in in varchar2) return varchar2 is
  user_name varchar2(50):=substr(client_user_in, 1, 50);
begin
  if lower(user_name)='unknown' then
    user_name:=null;
  end if;
  user_name:=substr(regexp_replace(regexp_replace(coalesce(user_name, sys_context('USERENV','CLIENT_IDENTIFIER'), sys_context('USERENV','OS_USER')),
             '^(.*)@.*$', '\1'),
             '^.*CN=([^, ]*).*$', '\1'),
    1, 20);
  return user || case when user_name is not null and lower(user) <> lower(user_name) then ' ('||user_name||')' end;
end;
/

------- CreateDDL statement separator -------
create global temporary table SESSION_VARIABLES (
    NAME VARCHAR(255),
    VALUE VARCHAR(255),
    constraint SESS_VAR_PK primary key (NAME)
) ON COMMIT PRESERVE ROWS;

#if( $bypassFunctionality )
------- CreateDDL statement separator -------
create or replace function is_bypassed(object_name in varchar2) return number is
  bypass_regex varchar2(255);
begin
  select value into bypass_regex
  from SESSION_VARIABLES
  where name='yagen.bypass.regex' and REGEXP_LIKE(object_name, value);

  if bypass_regex is null then
    return 0;
  end if;

  return 1;
exception when no_data_found then
  return 0;
end;
/
#end
#end

#if( $is_hsql )
------- CreateDDL statement separator -------
CREATE FUNCTION sys_guid() RETURNS char(32)
LANGUAGE JAVA DETERMINISTIC NO SQL
EXTERNAL NAME 'CLASSPATH:com.github.gekoh.yagen.util.DBHelper.createUUID'
;

------- CreateDDL statement separator -------
CREATE FUNCTION sys_context_internal(namespace varchar(255), param varchar(255)) RETURNS varchar(255)
LANGUAGE JAVA DETERMINISTIC NO SQL
EXTERNAL NAME 'CLASSPATH:com.github.gekoh.yagen.util.DBHelper.getSysContext'
;

------- CreateDDL statement separator -------
create global temporary table SESSION_VARIABLES (
    NAME VARCHAR(255),
    VALUE VARCHAR(255),
    constraint SESS_VAR_PK primary key (NAME)
) ON COMMIT PRESERVE ROWS; -- align with postrgresql and oracle behavior

------- CreateDDL statement separator -------
CREATE FUNCTION sys_context(namespace varchar(255), param varchar(255)) RETURNS varchar(255)
begin atomic
  declare var_found VARCHAR(50);
  declare exit handler for SQLEXCEPTION
      return sys_context_internal(namespace, param);
  if namespace='USERENV' and param='CLIENT_IDENTIFIER' then
    select value into var_found from SESSION_VARIABLES where NAME='CLIENT_IDENTIFIER';
    return var_found;
  end if;
  return sys_context_internal(namespace, param);
end;

------- CreateDDL statement separator -------
CREATE FUNCTION systimestamp_9() RETURNS timestamp(9)
  LANGUAGE JAVA DETERMINISTIC NO SQL
  EXTERNAL NAME 'CLASSPATH:com.github.gekoh.yagen.util.DBHelper.getCurrentTimestamp'
;

------- CreateDDL statement separator -------
CREATE FUNCTION regexp_like(s VARCHAR(4000), regexp VARCHAR(500))
  RETURNS BOOLEAN
  LANGUAGE JAVA DETERMINISTIC NO SQL
  EXTERNAL NAME 'CLASSPATH:com.github.gekoh.yagen.util.DBHelper.regexpLike'
;

------- CreateDDL statement separator -------
CREATE FUNCTION regexp_like(s VARCHAR(4000), regexp VARCHAR(500), flags VARCHAR(10))
  RETURNS BOOLEAN
  LANGUAGE JAVA DETERMINISTIC NO SQL
  EXTERNAL NAME 'CLASSPATH:com.github.gekoh.yagen.util.DBHelper.regexpLikeFlags'
;

#if( $bypassFunctionality )
------- CreateDDL statement separator -------
CREATE FUNCTION is_statically_bypassed(object_name VARCHAR(100))
  RETURNS BOOLEAN
  LANGUAGE JAVA DETERMINISTIC NO SQL
  EXTERNAL NAME 'CLASSPATH:com.github.gekoh.yagen.util.DBHelper.isStaticallyBypassed'
;

------- CreateDDL statement separator -------
CREATE FUNCTION is_bypassed(object_name VARCHAR(100))
  RETURNS INTEGER
begin atomic
  declare bypass_regex VARCHAR(255);
  declare exit handler for not found
    return 0;

  select value into bypass_regex
  from SESSION_VARIABLES
  where name='yagen.bypass.regex';

  if REGEXP_MATCHES(object_name, bypass_regex) then
    return 1;
  end if;

  return 0;
end;
#end

------- CreateDDL statement separator -------
CREATE FUNCTION get_audit_user(client_user_in VARCHAR(50)) RETURNS varchar(50)
begin atomic
  declare user_name VARCHAR(50);
  set user_name = client_user_in;

  if lower(user_name)='unknown' then
    set user_name = null;
  end if;

  set user_name = substr(regexp_replace(regexp_replace(coalesce(user_name, sys_context('USERENV','CLIENT_IDENTIFIER'), sys_context('USERENV','OS_USER')),
    '^(.*)@.*$', '\1'),
    '^.*CN=([^, ]*).*$', '\1'),
    1, 20);
  return user || case when user_name is not null and lower(user) <> lower(user_name) then ' ('||user_name||')' end;
end;
#end

#if( $is_postgres )

------- CreateDDL statement separator -------
create temporary table SESSION_VARIABLES (
    NAME VARCHAR(255),
    VALUE VARCHAR(255),
    constraint SESS_VAR_PK primary key (NAME)
) ON COMMIT PRESERVE ROWS;

------- CreateDDL statement separator -------
CREATE EXTENSION "uuid-ossp";

------- CreateDDL statement separator -------
CREATE FUNCTION sys_guid() RETURNS VARCHAR AS $$
declare
	guid varchar;
begin
    SELECT upper(REPLACE(uuid_generate_v4()::varchar, '-', '')) into guid;
    return guid;
end;
$$ LANGUAGE PLPGSQL;

------- CreateDDL statement separator -------
CREATE FUNCTION raise_application_error(code int, message varchar) RETURNS void AS $$
begin
    raise exception '%: %', code, message using errcode = abs(code)::varchar;
end;
$$ LANGUAGE PLPGSQL;

------- CreateDDL statement separator -------
CREATE FUNCTION sys_context(namespace varchar,parameter varchar) RETURNS VARCHAR AS $$
DECLARE
  override_user varchar;
begin
  if 'USERENV' = namespace then
    if 'DB_NAME' = parameter then
      return 'PostgreSQL';
    elsif 'OS_USER' = parameter then
      return null;
    elsif 'CLIENT_IDENTIFIER' = parameter then
      override_user = get_session_variable(parameter);

      if override_user is not null then
        return override_user;
      else
        return session_user;
      end if;
    end if;
  end if;
end;
$$ LANGUAGE PLPGSQL;

------- CreateDDL statement separator -------
create function get_audit_user(client_user_in in VARCHAR) RETURNS VARCHAR AS $$
declare
  user_name varchar := substr(client_user_in, 1, 50);
begin
  if lower(user_name)='unknown' then
    user_name:=null;
  end if;
  user_name:=substr(regexp_replace(regexp_replace(coalesce(user_name, sys_context('USERENV','CLIENT_IDENTIFIER'), sys_context('USERENV','OS_USER')),
             '^(.*)@.*$', '\1'),
             '^.*CN=([^, ]*).*$', '\1'),
    1, 20);
  return user || case when user_name is not null and lower(user) <> lower(user_name) then ' ('||user_name||')' end;
end;
$$ LANGUAGE PLPGSQL;

#if( $bypassFunctionality )
------- CreateDDL statement separator -------
CREATE FUNCTION is_bypassed(object_name varchar) RETURNS NUMERIC AS $$
declare bypass_regex varchar(255);
begin
  select value into bypass_regex
  from SESSION_VARIABLES
  where name='yagen.bypass.regex';

  if REGEXP_MATCHES(object_name, bypass_regex) is not null then
    return 1;
  end if;
  return 0;
exception when others then
  return 0;
end;
$$ LANGUAGE PLPGSQL;
#end

------- CreateDDL statement separator -------
CREATE VIEW dual AS
  select cast('X' as varchar) DUMMY
;

------- CreateDDL statement separator -------
CREATE FUNCTION audit_trigger_function()
  RETURNS trigger AS $$
BEGIN
#if( $bypassFunctionality )
    if is_bypassed(upper(tg_table_name)) = 1 then
        return new;
    end if;

#end
    if TG_OP = 'INSERT' then
        new.created_at := localtimestamp;
        new.created_by := coalesce(new.created_by, sys_context('USERENV','CLIENT_IDENTIFIER'), sys_context('USERENV','OS_USER'), user);
        new.last_modified_at := null;
        new.last_modified_by := null;
    elsif TG_OP = 'UPDATE' then
        new.created_at := old.created_at;
        new.created_by := old.created_by;
        if not(new.last_modified_at is not null and (old.last_modified_at is null or new.last_modified_at <> old.last_modified_at )) then
          new.last_modified_by := coalesce(sys_context('USERENV','CLIENT_IDENTIFIER'), sys_context('USERENV','OS_USER'), user);
        end if;
        new.last_modified_at := localtimestamp;
    end if;
    return new;
END;
$$ LANGUAGE 'plpgsql';

------- CreateDDL statement separator -------
CREATE FUNCTION set_session_variable(var_name varchar, var_value varchar) RETURNS void AS $$
declare
    affected_rows integer;
begin
    begin
        with stmt as (
          update SESSION_VARIABLES
            set value = var_value
            where name = var_name
            returning 1
        )

        select count(*) into affected_rows from stmt;
        if affected_rows=1 then
          return;
        end if;

    exception when others then
        create temporary table SESSION_VARIABLES (
          NAME VARCHAR(255),
          VALUE VARCHAR(255),
          constraint SESS_VAR_PK primary key (NAME)
        ) ON COMMIT PRESERVE ROWS;
    end;

    insert into SESSION_VARIABLES (name, value)
        values (var_name, var_value);
end;
$$ LANGUAGE PLPGSQL;

------- CreateDDL statement separator -------
CREATE FUNCTION remove_session_variable(var_name varchar) RETURNS void AS $$
begin
    delete from SESSION_VARIABLES
        where name = var_name;
        exception when others then null;
end;
$$ LANGUAGE PLPGSQL;

------- CreateDDL statement separator -------
CREATE FUNCTION get_session_variable(var_name varchar) RETURNS varchar AS $$
DECLARE
    ret varchar;
begin
    select value into ret from SESSION_VARIABLES where name = var_name;
    return ret;

    exception when others then
        return null;
end;
$$ LANGUAGE PLPGSQL;

#end
