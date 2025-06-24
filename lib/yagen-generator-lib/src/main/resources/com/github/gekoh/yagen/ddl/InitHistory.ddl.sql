#if( $is_oracle )
------- CreateDDL statement separator -------
create global temporary table HST_CURRENT_TRANSACTION (
  transaction_id varchar2(4000 char),
  transaction_timestamp ${timestampType},
  constraint hsttr_transaction_id_PK primary key (transaction_id)
);

------- CreateDDL statement separator -------
create global temporary table HST_MODIFIED_ROW (
  table_name varchar2(30 char),
  row_id rowid,
  operation varchar2(1 char),
  hst_table_name varchar2(30 char),
  hst_uuid varchar2(32 char),
  constraint hstmod_rowid_tablename_PK primary key (row_id, table_name)
);

------- CreateDDL statement separator -------
create procedure set_transaction_timestamp(timestamp_in in timestamp) is
begin
  insert into HST_CURRENT_TRANSACTION (TRANSACTION_ID, TRANSACTION_TIMESTAMP)
    values (DBMS_TRANSACTION.LOCAL_TRANSACTION_ID, timestamp_in);
end;
/

------- CreateDDL statement separator -------
create or replace function update_transaction_timestamp(exclude_hst_uuid_in in varchar2) return timestamp is
  transaction_timestamp_found ${timestampType};
  new_transaction_timestamp ${timestampType};
begin
    select transaction_timestamp into transaction_timestamp_found
    from HST_CURRENT_TRANSACTION
    where transaction_id=DBMS_TRANSACTION.LOCAL_TRANSACTION_ID;

    new_transaction_timestamp:=get_audit_timestamp();
    update HST_CURRENT_TRANSACTION set transaction_timestamp=new_transaction_timestamp
    where transaction_id=DBMS_TRANSACTION.LOCAL_TRANSACTION_ID;

    for data in (select HST_TABLE_NAME, HST_UUID from HST_MODIFIED_ROW where HST_UUID<>exclude_hst_uuid_in) loop

        execute immediate 'update '||data.HST_TABLE_NAME||' set transaction_timestamp=:new_ts where hst_uuid=:hst_uuid'
          using new_transaction_timestamp, data.HST_UUID;
    end loop;

    for data in (select distinct HST_TABLE_NAME from HST_MODIFIED_ROW where HST_UUID<>exclude_hst_uuid_in) loop

        execute immediate 'update '||data.HST_TABLE_NAME||' h set invalidated_at=:new_ts where transaction_timestamp < :new_ts1 and operation <> ''D'' and invalidated_at = :old_ts'
            using new_transaction_timestamp, new_transaction_timestamp, transaction_timestamp_found;
    end loop;

    return new_transaction_timestamp;
end;
/
#end

#if( $is_hsql )
------- CreateDDL statement separator -------
create global temporary table HST_CURRENT_TRANSACTION (
  transaction_id bigint,
  transaction_timestamp ${timestampType},
  constraint hsttr_transaction_id_PK primary key (transaction_id)
);

------- CreateDDL statement separator -------
create global temporary table HST_MODIFIED_ROW (
  table_name varchar(30),
  row_id varchar(64),
  operation char(1),
  hst_table_name varchar(30),
  hst_uuid varchar(32),
  constraint hstmod_rowid_tablename_PK primary key (row_id, table_name)
);

------- CreateDDL statement separator -------
create function txid_current() returns bigint
begin atomic
-- using always transaction id = 0 since internal id returned by
-- TRANSACTION_ID() seems to be different for every statement
-- always having the same value is no issue with our current usage
-- NOTE! because of a weird deadlock in hsql we cannot use this function, so see procedure set_transaction_timestamp
-- and history triggers where the value is directly used instead of this function
  return 0;
end;
/

------- CreateDDL statement separator -------
create procedure set_transaction_timestamp(in timestamp_in ${timestampType})
begin atomic
  declare transaction_id_used bigint;
  set transaction_id_used=0;--txid_current();
  insert into HST_CURRENT_TRANSACTION (TRANSACTION_ID, TRANSACTION_TIMESTAMP)
    values (transaction_id_used, timestamp_in);
end;
#end

#if( $is_postgres )
------- CreateDDL statement separator -------
create table if not exists HST_CURRENT_TRANSACTION (
    transaction_id bigint,
    transaction_timestamp ${timestampType},
    constraint hsttr_transaction_id_PK primary key (transaction_id)
);

------- CreateDDL statement separator -------
create table if not exists HST_MODIFIED_ROW (
    transaction_id bigint,
    table_name varchar(30),
    row_id varchar(100),
    operation char(1),
    hst_table_name varchar(30),
    hst_uuid varchar(32),
    constraint hstmod_rowid_tablename_PK primary key (transaction_id, row_id, table_name)
);

------- CreateDDL statement separator -------
create index if not exists hstmod_rowid_tablename_IX on HST_MODIFIED_ROW (row_id, table_name);

------- CreateDDL statement separator -------
do $_$
begin
    CREATE FUNCTION HST_CURRENT_TRANSACTION_TRG_FCT()
      RETURNS trigger AS $$
    begin
      delete from HST_CURRENT_TRANSACTION where transaction_id=new.transaction_id;
      delete from HST_MODIFIED_ROW where transaction_id=new.transaction_id;
      return old;
    end;
    $$ LANGUAGE 'plpgsql';
exception
    when duplicate_function then null;
end; $_$;

------- CreateDDL statement separator -------
/*
  This simulates the behaviour of global temporary tables since there is only
  temporary tables available in postgresql.
  So on commit we remove the inserted rows via trigger function HST_CURRENT_TRANSACTION_TRG_FCT.

  Only create trigger if not existing, otherwise we get a weird error if trigger is removed and recreated
  in one transaction when data has already been inserted [ERROR: could not find trigger 257927] -> random id of trigger
 */

do $_$
begin
    create constraint trigger HST_CURRENT_TRANSACTION_TRG after insert
    on HST_CURRENT_TRANSACTION initially deferred for each row
    execute procedure HST_CURRENT_TRANSACTION_TRG_FCT();
exception
    when duplicate_object then null;
end; $_$;

------- CreateDDL statement separator -------
create or replace function set_transaction_timestamp(timestamp_in timestamp) RETURNS void AS $$
declare
  transaction_id_used bigint;
begin
  transaction_id_used := txid_current();
  insert into HST_CURRENT_TRANSACTION (TRANSACTION_ID, TRANSACTION_TIMESTAMP)
    values (transaction_id_used, timestamp_in);
end;
$$ LANGUAGE PLPGSQL;
#end
