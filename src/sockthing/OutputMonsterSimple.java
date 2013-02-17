
package sockthing;

import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.NetworkParameters;


import java.math.BigInteger;


public class OutputMonsterSimple implements OutputMonster
{
    protected Address pay_to;

    public OutputMonsterSimple(Config conf, NetworkParameters params)
        throws com.google.bitcoin.core.AddressFormatException
    {
        conf.require("pay_to_address");

        pay_to = new Address(params,conf.get("pay_to_address"));


    }

    public void addOutputs(PoolUser pu, Transaction tx, BigInteger total_value, BigInteger fee_total)
    {
        tx.addOutput(total_value, pay_to);
    }
}
