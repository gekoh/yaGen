create or replace view ${objectName} as
select h.*,
#if( $is_oracle )
       row_number() over (partition by #foreach( $pkColumn in $pkColumns )#if($foreach.count > 1), #{end}h.${pkColumn}#end order by h.TRANSACTION_TIMESTAMP desc)
#else
       (select count(*)+1 from ${hstTableName} where #foreach( $pkColumn in $pkColumns )${pkColumn}=h.${pkColumn} and #end TRANSACTION_TIMESTAMP>h.TRANSACTION_TIMESTAMP and OPERATION='I')
#end
        as rev_materialization_nr
from ${hstTableName} h
    left join ${hstTableName} hd on
#foreach( $pkColumn in $pkColumns )
#if( $!{columnMap.get($pkColumn).isNullable()} )
              ((hd.${pkColumn} is null and h.${pkColumn} is null) or hd.${pkColumn}=h.${pkColumn}) and
#else
              hd.${pkColumn}=h.${pkColumn} and
#end
#end
          hd.${histColName}=h.INVALIDATED_AT and hd.OPERATION='D'
where (h.INVALIDATED_AT is null and h.OPERATION<>'D')
   or hd.hst_uuid is not null
;