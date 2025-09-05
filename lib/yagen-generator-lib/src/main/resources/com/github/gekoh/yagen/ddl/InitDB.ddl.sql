#if( $configuration['yagen.generator.audit.user.maxlen'])
#set( $auditUserLen = $configuration['yagen.generator.audit.user.maxlen'] )
#else
#set( $auditUserLen = 35 )
#end

#if( $is_oracle )
------- CreateDDL statement separator -------
create or replace function get_audit_user(client_user_in in varchar2) return varchar2
    AUTHID CURRENT_USER is
  user_name varchar2(50):=substr(client_user_in, 1, 50);
begin
  if lower(user_name)='unknown' then
    user_name:=null;
  end if;
  user_name:=substr(regexp_replace(regexp_replace(coalesce(user_name, sys_context('USERENV','CLIENT_IDENTIFIER'), sys_context('USERENV','OS_USER')),
             '^(.*)@.*$', '\1'),
             '^.*CN=([^, ]*).*$', '\1'),
    1, $auditUserLen /*user column length*/ -3 -length(user));
  return user || case when user_name is not null and lower(user) <> lower(user_name) then ' ('||user_name||')' else '' end;
end;
/

------- CreateDDL statement separator -------
create or replace function f_sysdate return date as
begin
    return sysdate;
end;
/

------- CreateDDL statement separator -------
create or replace function get_audit_timestamp return timestamp is
begin
return systimestamp;
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
CREATE FUNCTION f_sysdate() RETURNS ${timestampType}
  LANGUAGE JAVA DETERMINISTIC NO SQL
  EXTERNAL NAME 'CLASSPATH:com.github.gekoh.yagen.util.DBHelper.getCurrentTimestamp'
;

------- CreateDDL statement separator -------
CREATE FUNCTION systimestamp_9() RETURNS ${timestampType}
  LANGUAGE JAVA DETERMINISTIC NO SQL
  EXTERNAL NAME 'CLASSPATH:com.github.gekoh.yagen.util.DBHelper.getCurrentTimestamp'
;

------- CreateDDL statement separator -------
create function get_audit_timestamp() returns ${timestampType}
begin atomic
return systimestamp_9();
end;
/

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
    1, $auditUserLen /*user column length*/ -3 -length(user));
  return user || case when user_name is not null and lower(user) <> lower(user_name) then ' ('||user_name||')' else '' end;
end;
#end

#if( $is_postgres )

------- CreateDDL statement separator -------
create temporary table if not exists SESSION_VARIABLES (
    NAME VARCHAR(255),
    VALUE VARCHAR(255),
    constraint SESS_VAR_PK primary key (NAME)
) ON COMMIT PRESERVE ROWS;

#set( $uuidExtension = $configuration['yagen.generator.postgres.extension.uuid-ossp'] )
#if( $uuidExtension == 'create' )
------- CreateDDL statement separator -------
CREATE EXTENSION "uuid-ossp";

#end
------- CreateDDL statement separator -------
CREATE or replace FUNCTION sys_guid() RETURNS VARCHAR AS $$
declare
	guid varchar;
begin
    SELECT upper(REPLACE(#if( $uuidExtension == 'create' )uuid_generate_v4()#{else}gen_random_uuid()#end::varchar, '-', '')) into guid;
    return guid;
end;
$$ LANGUAGE PLPGSQL;

------- CreateDDL statement separator -------
CREATE or replace FUNCTION systimestamp() RETURNS ${timestampType} AS $$
begin
    return clock_timestamp() at time zone (select reset_val from pg_settings where name='TimeZone');
end;
$$ LANGUAGE PLPGSQL;

------- CreateDDL statement separator -------
CREATE or replace FUNCTION f_sysdate() RETURNS timestamp AS $$
begin
return systimestamp();
end;
$$ LANGUAGE PLPGSQL;

------- CreateDDL statement separator -------
create or replace function get_audit_timestamp() RETURNS ${timestampType} AS $$
begin
return systimestamp();
end;
$$ LANGUAGE PLPGSQL;

------- CreateDDL statement separator -------
CREATE or replace FUNCTION raise_application_error(code int, message varchar) RETURNS void AS $$
begin
    raise exception '%: %', code, message using errcode = abs(code)::varchar;
end;
$$ LANGUAGE PLPGSQL;

------- CreateDDL statement separator -------
CREATE or replace FUNCTION sys_context(namespace varchar,parameter varchar) RETURNS VARCHAR AS $$
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
create or replace function get_audit_user(client_user_in in VARCHAR) RETURNS VARCHAR AS $$
declare
  user_name varchar := substr(client_user_in, 1, 50);
begin
  if lower(user_name)='unknown' then
    user_name:=null;
  end if;
  user_name:=substr(regexp_replace(regexp_replace(coalesce(user_name, sys_context('USERENV','CLIENT_IDENTIFIER'), sys_context('USERENV','OS_USER')),
             '^(.*)@.*$', '\1'),
             '^.*CN=([^, ]*).*$', '\1'),
    1, $auditUserLen /*user column length*/ -3 -length(user));
  return user || case when user_name is not null and lower(user) <> lower(user_name) then ' ('||user_name||')' else '' end;
end;
$$ LANGUAGE PLPGSQL;

#if( $bypassFunctionality )

------- CreateDDL statement separator -------
CREATE or replace FUNCTION is_bypassed(object_name varchar) RETURNS NUMERIC AS $$
declare bypass_regex varchar(255);
begin
  select value into bypass_regex
  from SESSION_VARIABLES
  where name='yagen.bypass.regex';

  if object_name ~ bypass_regex then
    return 1;
  end if;
  return 0;
exception when others then
  return 0;
end;
$$ LANGUAGE PLPGSQL;
#end

------- CreateDDL statement separator -------
CREATE or replace VIEW dual AS
  select cast('X' as varchar) DUMMY
;

------- CreateDDL statement separator -------
CREATE or replace FUNCTION ora_hash(text_in in VARCHAR) RETURNS NUMERIC AS $$
BEGIN
return hashtext(text_in);
END;
$$ LANGUAGE 'plpgsql';

------- CreateDDL statement separator -------
CREATE or replace FUNCTION audit_trigger_function()
  RETURNS trigger AS $$
BEGIN
#if( $bypassFunctionality )
    if is_bypassed(upper(tg_table_name)) = 1 then
        return new;
    end if;

#end
    if TG_OP = 'INSERT' then
        new.created_at := get_audit_timestamp();
        new.created_by := get_audit_user(new.created_by);
        new.last_modified_at := null;
        new.last_modified_by := null;
    elsif TG_OP = 'UPDATE' then
        new.created_at := old.created_at;
        new.created_by := old.created_by;
        if new.last_modified_at is not null and (old.last_modified_at is null or new.last_modified_at <> old.last_modified_at ) then
            -- using last_modified_by injected by application
            new.last_modified_by := get_audit_user(new.last_modified_by);
        else
            new.last_modified_by := get_audit_user(null);
        end if;
        new.last_modified_at := get_audit_timestamp();
    end if;
    return new;
END;
$$ LANGUAGE 'plpgsql';

------- CreateDDL statement separator -------
CREATE or replace FUNCTION audit_trigger_function_single()
    RETURNS trigger AS $$
declare
  last_modified_at_colname varchar;
  last_modified_by_colname varchar;
BEGIN
#if( $bypassFunctionality )
    if is_bypassed(upper(tg_table_name)) = 1 then
        return new;
end if;

#end
    if TG_NARGS > 0 then
        last_modified_at_colname := TG_ARGV[0];
    end if;
    if TG_NARGS > 1 then
        last_modified_by_colname := TG_ARGV[1];
    end if;
    -- dynamic column names not yet supported, only detect if last_modified_by is existing
    if last_modified_by_colname is not null then
        if new.last_modified_by is not null and (old.last_modified_by is null or new.last_modified_by <> old.last_modified_by ) then
            -- using last_modified_by injected by application
            new.last_modified_by := get_audit_user(new.last_modified_by);
        else
            new.last_modified_by := get_audit_user(null);
        end if;
    end if;
    new.last_modified_at := get_audit_timestamp();
return new;
END;
$$ LANGUAGE 'plpgsql';

------- CreateDDL statement separator -------
CREATE or replace FUNCTION set_session_variable(var_name varchar, var_value varchar) RETURNS void AS $$
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
CREATE or replace FUNCTION remove_session_variable(var_name varchar) RETURNS void AS $$
begin
    delete from SESSION_VARIABLES
        where name = var_name;
        exception when others then null;
end;
$$ LANGUAGE PLPGSQL;

------- CreateDDL statement separator -------
CREATE or replace FUNCTION get_session_variable(var_name varchar) RETURNS varchar AS $$
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
