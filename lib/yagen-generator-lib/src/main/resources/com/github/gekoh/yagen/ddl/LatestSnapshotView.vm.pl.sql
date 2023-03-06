create or replace view ${objectName} as
select h.*,
#if( $is_oracle )
       row_number() over (partition by #foreach( $pkColumn in $pkColumns )#if($foreach.count > 1), #{end}${pkColumn}#end order by TRANSACTION_TIMESTAMP desc)
#else
       (select count(*)+1 from ${hstTableName} where #foreach( $pkColumn in $pkColumns )${pkColumn}=h.${pkColumn} and #end TRANSACTION_TIMESTAMP>h.TRANSACTION_TIMESTAMP and OPERATION='I')
#end
        as rev_materialization_nr
from ${hstTableName} h
where (INVALIDATED_AT is null and OPERATION<>'D')
   or exists (
        select 1
        from ${hstTableName}
        where
#foreach( $pkColumn in $pkColumns )
#if( $!{columnMap.get($pkColumn).nullable} )
              ((${pkColumn} is null and h.${pkColumn} is null) or ${pkColumn}=h.${pkColumn}) and
#else
              ${pkColumn}=h.${pkColumn} and
#end
#end
          ${histColName}=h.INVALIDATED_AT and OPERATION='D'
    )
;