#if( $is_oracle )
create or replace view ${viewName} as
with
changelog as (
${changelogQueryString}
),
mut as (
	select
    l.uuid CHANGELOG_UUID,
	l.changeset_uuid,
	  l.created_at CHANGE_TIMESTAMP,
	  l.mutation_type,
	  l.entity_uuid UUID,
    l.CREATED_AT,
    l.CREATED_BY,
    l.LAST_MODIFIED_AT,
    l.LAST_MODIFIED_BY
#foreach( $selColumn in $columns )
  , CASE l.column_name WHEN '${selColumn.toUpperCase()}' THEN #if
  ( $numericColumns.contains($selColumn) )cast(l.new_value as $numericColumnDefinitions[$selColumn])#elseif
  ( $timestampColumns.contains($selColumn) )to_timestamp(l.new_value, 'yyyy-mm-dd hh24:mi:ss.ff')#elseif
  ( $clobColumns.contains($selColumn) )nvl(l.new_long_value, empty_clob())#{else}nvl(l.new_value, chr(0))#end END as ${selColumn}
#end
	from
	  changelog l
	where
	  l.TABLE_NAME='${tableName.toUpperCase()}' and l.COLUMN_NAME in (
#foreach( $column in $columns )
#if( $foreach.count > 1 )       ,#{else}        #end'${column.toUpperCase()}'
#end
      )
)
select * from (
  select
    TML_UUID,
    CREATED_AT,
    CREATED_BY,
    LAST_MODIFIED_AT,
    LAST_MODIFIED_BY,
    TML_PHASE,
    UUID,
#foreach( $column in $columns )
#if( $numericColumns.contains($column) || $timestampColumns.contains($column) )
    ${column},
#elseif( $clobColumns.contains($column) )
    case when dbms_lob.compare(${column}, empty_clob()) = 0 then null else ${column} end ${column},
#else
    decode(${column}, chr(0), null, ${column}) ${column},
#end
#end
    EFFECTIVE_TIMESTAMP_FROM,
    lead(EFFECTIVE_TIMESTAMP_FROM) over (partition by UUID order by EFFECTIVE_TIMESTAMP_FROM nulls first) EFFECTIVE_TIMESTAMP_TO
  from (
    select *
    from (
        select
          TML_UUID,
          TML_PHASE,
          UUID,
          first_value(CREATED_AT) ignore nulls over (partition by UUID order by EFFECTIVE_TIMESTAMP_FROM nulls first) CREATED_AT,
          first_value(CREATED_BY) ignore nulls over (partition by UUID order by EFFECTIVE_TIMESTAMP_FROM nulls first) CREATED_BY,
          last_value(LAST_MODIFIED_AT) ignore nulls over (partition by UUID order by EFFECTIVE_TIMESTAMP_FROM nulls first) LAST_MODIFIED_AT,
          last_value(LAST_MODIFIED_BY) ignore nulls over (partition by UUID order by EFFECTIVE_TIMESTAMP_FROM nulls first) LAST_MODIFIED_BY,
#foreach( $column in $columns )
#if( $clobColumns.contains($column) )
        -- analytic functions seem not to work with CLOB type columns
          coalesce(${column},
            (
              select coalesce(NEW_LONG_VALUE, empty_clob()) ${column}
              from changelog mi
              where ENTITY_UUID=o_tml.UUID
              and CREATED_AT=(select max(CREATED_AT) from changelog where ENTITY_UUID=mi.ENTITY_UUID and CREATED_AT<o_tml.EFFECTIVE_TIMESTAMP_FROM and COLUMN_NAME=mi.COLUMN_NAME)
              and COLUMN_NAME='${column.toUpperCase()}'
            ),
            (
              select ${column}
              from ${tableName}
              where UUID=o_tml.UUID
            )
          ) ${column},
#else
              nth_value(${column}, 1) ignore nulls over (partition by UUID order by EFFECTIVE_TIMESTAMP_FROM desc nulls last rows between current row and unbounded following) ${column},
#end
#end
          EFFECTIVE_TIMESTAMP_FROM,
          REV_VERSION_IN_CHANGESET_NR
        from (
          select
#foreach( $pkColumn in $pkColumns )e.${pkColumn}||'-'||#{end}coalesce(e.LAST_MODIFIED_AT,e.CREATED_AT) TML_UUID,
            'L' TML_PHASE,
#foreach( $pkColumn in $pkColumns )#if($foreach.count>1)||'-'||#{end}e.${pkColumn}#{end} UUID,
            e.CREATED_AT,
            e.CREATED_BY,
            e.LAST_MODIFIED_AT,
            e.LAST_MODIFIED_BY,
#foreach( $column in $columns )
            e.${column},
#end
            null EFFECTIVE_TIMESTAMP_FROM,
            null REV_VERSION_IN_CHANGESET_NR
          from
            ${tableName} e
          union all
          select
            CHANGELOG_UUID TML_UUID,
            case when MUTATION_TYPE='TERMINATE' then 'T' else 'M' end TML_PHASE,
            UUID,
            CREATED_AT,
            CREATED_BY,
            LAST_MODIFIED_AT,
            LAST_MODIFIED_BY,
#foreach( $column in $columns )
            ${column},
#end
            EFFECTIVE_TIMESTAMP_FROM,
            REV_VERSION_IN_CHANGESET_NR
          from (
              select
                m.CHANGELOG_UUID,
                m.MUTATION_TYPE,
                m.UUID,
                m.CREATED_AT,
                m.CREATED_BY,
                m.LAST_MODIFIED_AT,
                m.LAST_MODIFIED_BY,
#foreach( $column in $columns )
                m.${column},
#end
                m.CHANGE_TIMESTAMP EFFECTIVE_TIMESTAMP_FROM,
                row_number() over (partition by m.CHANGESET_UUID, m.UUID order by m.CHANGE_TIMESTAMP desc) REV_VERSION_IN_CHANGESET_NR
              from
                mut m
            )
        ) o_tml
      )
    where REV_VERSION_IN_CHANGESET_NR is null or REV_VERSION_IN_CHANGESET_NR=1
  )
)
where TML_PHASE<>'T'
;
#else
------- CreateDDL statement separator -------
create or replace view CHANGELOG_ROW_V as
with
recursive changeset_tree (PRECURSOR_CHANGESET_UUID, UUID, CHANGESET_STATUS) AS (
    select PRECURSOR_CHANGESET_UUID, UUID, CHANGESET_STATUS
    from
        DSM_CHANGESET
    where CHANGESET_STATUS='READY_TO_DEPLOY'
    UNION
    select s.PRECURSOR_CHANGESET_UUID, s.UUID, s.CHANGESET_STATUS
    from
        DSM_CHANGESET s
        join changeset_tree t on s.PRECURSOR_CHANGESET_UUID=t.UUID
)
select l.*, #if( $is_postgres )row_number() over()#{else}rownum()#{end} as row_nr
from
    dsm_changelog l
    join changeset_tree s on l.CHANGESET_UUID=s.uuid
;

------- CreateDDL statement separator -------
create or replace view ${viewName}_base as
with mut as (
	select
    l.row_nr,
    l.uuid CHANGELOG_UUID,
    l.created_at CHANGE_TIMESTAMP,
    l.mutation_type,
    l.entity_uuid UUID,
    l.CREATED_AT,
    l.CREATED_BY,
    l.LAST_MODIFIED_AT,
    l.LAST_MODIFIED_BY
#foreach( $selColumn in $columns )
  , CASE l.column_name WHEN '${selColumn.toUpperCase()}' THEN #if
  ( $numericColumns.contains($selColumn) )cast(l.new_value as $numericColumnDefinitions[$selColumn])#elseif
  ( $timestampColumns.contains($selColumn) )to_timestamp(l.new_value, 'yyyy-mm-dd hh24:mi:ss.ff')#elseif
  ( $clobColumns.contains($selColumn) )coalesce(l.new_long_value,'')#{else}coalesce(l.new_value,'')#end END as ${selColumn}
#end
	from
	  CHANGELOG_ROW_V l
	where
	  l.TABLE_NAME='${tableName.toUpperCase()}' and l.COLUMN_NAME in (
#foreach( $column in $columns )
#if( $foreach.count > 1 )       ,#{else}        #end'${column.toUpperCase()}'
#end
      )
),
tml as (
  select
    #foreach( $pkColumn in $pkColumns )e.${pkColumn}||'-'||#{end}coalesce(e.LAST_MODIFIED_AT,e.CREATED_AT) TML_UUID,
    'L' TML_PHASE,
    #foreach( $pkColumn in $pkColumns )#if($foreach.count>1)||'-'||#{end}e.${pkColumn}#{end} UUID,
    e.CREATED_AT,
    e.CREATED_BY,
    e.LAST_MODIFIED_AT,
    e.LAST_MODIFIED_BY,
#foreach( $column in $columns )
    e.${column},
#end
    null EFFECTIVE_TIMESTAMP_FROM,
    1 as ROW_NR
  from
    ${tableName} e
  union all
  select
    m.CHANGELOG_UUID TML_UUID,
    case when m.MUTATION_TYPE='TERMINATE' then 'T' else 'M' end TML_PHASE,
    m.UUID,
    m.CREATED_AT,
    m.CREATED_BY,
    m.LAST_MODIFIED_AT,
    m.LAST_MODIFIED_BY,
#foreach( $column in $columns )
    m.${column},
#end
    m.CHANGE_TIMESTAMP EFFECTIVE_TIMESTAMP_FROM,
    m.ROW_NR
  from
    mut m
)
	select distinct
        o_tml.TML_UUID,
        o_tml.TML_PHASE,
        o_tml.UUID,
		coalesce(o_tml.CREATED_AT, (select CREATED_AT from tml where UUID=o_tml.UUID and (EFFECTIVE_TIMESTAMP_FROM is null or EFFECTIVE_TIMESTAMP_FROM<=o_tml.EFFECTIVE_TIMESTAMP_FROM) and CREATED_AT is not null order by EFFECTIVE_TIMESTAMP_FROM asc nulls last limit 1)) CREATED_AT,
		coalesce(o_tml.CREATED_BY, (select CREATED_BY from tml where UUID=o_tml.UUID and (EFFECTIVE_TIMESTAMP_FROM is null or EFFECTIVE_TIMESTAMP_FROM<=o_tml.EFFECTIVE_TIMESTAMP_FROM) and CREATED_BY is not null order by EFFECTIVE_TIMESTAMP_FROM asc nulls last limit 1)) CREATED_BY,
		coalesce(o_tml.LAST_MODIFIED_AT, (select LAST_MODIFIED_AT from tml where UUID=o_tml.UUID and (EFFECTIVE_TIMESTAMP_FROM is null or EFFECTIVE_TIMESTAMP_FROM<=o_tml.EFFECTIVE_TIMESTAMP_FROM) and LAST_MODIFIED_AT is not null order by EFFECTIVE_TIMESTAMP_FROM desc nulls last limit 1)) LAST_MODIFIED_AT,
		coalesce(o_tml.LAST_MODIFIED_BY, (select LAST_MODIFIED_BY from tml where UUID=o_tml.UUID and (EFFECTIVE_TIMESTAMP_FROM is null or EFFECTIVE_TIMESTAMP_FROM<=o_tml.EFFECTIVE_TIMESTAMP_FROM) and LAST_MODIFIED_BY is not null order by EFFECTIVE_TIMESTAMP_FROM desc nulls last limit 1)) LAST_MODIFIED_BY,
#foreach( $column in $columns )
		coalesce(${column}, (select ${column} from tml where UUID=o_tml.UUID and (EFFECTIVE_TIMESTAMP_FROM is null or EFFECTIVE_TIMESTAMP_FROM<=o_tml.EFFECTIVE_TIMESTAMP_FROM) and ${column} is not null order by EFFECTIVE_TIMESTAMP_FROM desc nulls last limit 1)) ${column},
#end
        o_tml.EFFECTIVE_TIMESTAMP_FROM
	from
		tml o_tml
	    left join DSM_CHANGELOG o_cl on o_tml.TML_UUID=o_cl.UUID
    where o_tml.EFFECTIVE_TIMESTAMP_FROM is null or (
            o_tml.EFFECTIVE_TIMESTAMP_FROM = (select max(EFFECTIVE_TIMESTAMP_FROM) from tml left join DSM_CHANGELOG cl on tml.TML_UUID=cl.UUID where tml.UUID=o_tml.UUID and cl.CHANGESET_UUID=o_cl.CHANGESET_UUID)
            and o_tml.ROW_NR = (select max(ROW_NR) from tml left join DSM_CHANGELOG cl on tml.TML_UUID=cl.UUID where tml.UUID=o_tml.UUID and cl.CHANGESET_UUID=o_cl.CHANGESET_UUID and o_tml.EFFECTIVE_TIMESTAMP_FROM=tml.EFFECTIVE_TIMESTAMP_FROM)
        )
;

------- CreateDDL statement separator -------
create or replace view ${viewName} as
select
  TML_UUID,
  TML_PHASE,
  UUID,
  CREATED_AT,
  CREATED_BY,
  LAST_MODIFIED_AT,
  LAST_MODIFIED_BY,
#foreach( $column in $columns )
#if( $numericColumns.contains($column) || $timestampColumns.contains($column) )
  ${column},
#else
  case when ${column}<>'' then ${column} end ${column},
#end
#end
  EFFECTIVE_TIMESTAMP_FROM,
  (select min(EFFECTIVE_TIMESTAMP_FROM) from ${viewName}_base where UUID=tml.UUID and (tml.EFFECTIVE_TIMESTAMP_FROM is null or EFFECTIVE_TIMESTAMP_FROM>tml.EFFECTIVE_TIMESTAMP_FROM)) as EFFECTIVE_TIMESTAMP_TO
from ${viewName}_base tml
where TML_PHASE<>'T'
;
#end