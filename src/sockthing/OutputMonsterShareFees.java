
package sockthing;

import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.NetworkParameters;


import java.math.BigInteger;

import java.util.List;
import java.util.LinkedList;

public class OutputMonsterShareFees implements OutputMonster
{
    protected List<Address> pay_to;
    protected NetworkParameters params;

    public OutputMonsterShareFees(Config conf, NetworkParameters params)
        throws com.google.bitcoin.core.AddressFormatException
    {
        this.params = params;
        conf.require("pay_to_address");

        pay_to = new LinkedList<Address>();

        for(String addr_str : conf.getList("pay_to_address"))
        {
            Address a = new Address(params,addr_str);
            pay_to.add(a);
        }
        System.out.println("Pay to: " + pay_to);


    }

    public void addOutputs(PoolUser pu, Transaction tx, BigInteger total_value, BigInteger fee_value)
    {
        fee_value = fee_value.divide(BigInteger.valueOf(2));
        BigInteger remaining = total_value.subtract(fee_value);

        BigInteger[] divmod = remaining.divideAndRemainder(BigInteger.valueOf(pay_to.size()));
        BigInteger per_output = divmod[0];
        BigInteger first_output = per_output.add(divmod[1]);

        boolean first=true;
        for(Address addr : pay_to)
        {
            if (first)
            {
                tx.addOutput(first_output, addr);
                first=false;
            }
            else
            {
                tx.addOutput(per_output, addr);
            }
        }

        try
        {
            Address user_addr = new Address(params, pu.getName());
            if (fee_value.compareTo(BigInteger.ZERO) > 0)
            {
                tx.addOutput(fee_value, user_addr);


            }
        }
        catch(com.google.bitcoin.core.AddressFormatException e)
        {
            throw new RuntimeException(e);
        }
    }
}
