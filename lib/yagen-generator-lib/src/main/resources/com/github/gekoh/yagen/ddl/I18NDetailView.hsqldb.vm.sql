-- view which always delivers a row for each language, when a description for a specific language
-- is not present, a description of another language will be delivered
create or replace view $i18nDetailTblName as
select
  x.${i18nFKColName}||'-'||x.language_cd AS ${I18N_COLUMN_COMPOSITE_ID},
  case when i18n.${i18nFKColName} is not null then #if( $columnMapping_IS_PERSISTENT.sqlType.contains("int") || $columnMapping_IS_PERSISTENT.sqlType.contains("num") )1 else 0#{else}'Y' else 'N'#end end AS ${I18N_COLUMN_IS_PERSISTENT}#foreach( $column in $columns ),
#if( $column == 'description' )
  case when i18n.${i18nFKColName} is not null then i18n.description else (
      select description
      from $i18nTblName alt
      where x.${i18nFKColName} = alt.${i18nFKColName}
#if( !$is_postgres )
        and rownum=1
#end
      order by language_cd
#if( $is_postgres )
      limit 1
#end
    ) end AS description#{else}#if (  $column == 'version' )
  case when i18n.${i18nFKColName} is not null then i18n.version else 0 end AS version#{else}#if (  $column == 'language_cd' || $column == ${i18nFKColName.toLowerCase()} )
  x.#{else}
  i18n.#{end}$column#end#end
#end

from
  (
    select *
    from (
        select distinct ${baseEntityPKColNames[0]} AS ${i18nFKColName}
        from $baseEntityTableName
      ) u,
      $LANGUAGE_VIEW_NAME l
  ) x
  left join $i18nTblName i18n on x.${i18nFKColName} = i18n.${i18nFKColName} and x.language_cd = i18n.language_cd