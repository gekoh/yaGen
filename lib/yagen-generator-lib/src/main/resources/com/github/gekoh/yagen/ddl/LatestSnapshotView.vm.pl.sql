create or replace view ${objectName} as
select *
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