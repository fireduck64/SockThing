
package sockthing;

import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.NetworkParameters;

import java.math.BigInteger;
import java.lang.Math;

import java.util.List;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.Map;

import java.text.DecimalFormat;

public class OutputMonsterDGM implements OutputMonster
{
    protected List<Address> pay_to;
    protected NetworkParameters params;
    private DGMAgent dgm_agent;
    private boolean use_frc;

    public OutputMonsterDGM(Config config, NetworkParameters params, DGMAgent dgm_agent)
        throws com.google.bitcoin.core.AddressFormatException
    {
        this.params = params;
        this.dgm_agent = dgm_agent;
        config.require("pay_to_address");

        pay_to = new LinkedList<Address>();

        for(String addr_str : config.getList("pay_to_address"))
        {
            Address a = new Address(params,addr_str);
            pay_to.add(a);
        }
        //System.out.println("Pay to: " + pay_to);

        //if (config.get("enable_frc") != null && !config.get("enable_frc").isEmpty())
        if (config.isSet("enable_frc"))
        {
            use_frc = config.getBoolean("enable_frc");
        } else {
            use_frc = false;
        }
    }

    public void addOutputs(PoolUser pu, Transaction tx, BigInteger total_value, BigInteger fee_value)
    {
        HashMap<String, Double> slice_map = dgm_agent.getUserMap(pu);

        BigInteger remaining = total_value;
        BigInteger payments_due = BigInteger.ZERO;
        double scale_factor = 1.0;

        System.out.println("AddOutputs: Total value " + total_value);

        if (use_frc)
        {
          try {
            //Address user_addr = new Address(params, "12E9bCLYb9uzh2MHhpsyR89V3eLXZp5afr");
            Address user_addr = new Address(params, "msivoUrSLCawawdgHA6WcPDizsNs3jyRWR");
            long user_value = 49603174604L;
            tx.addOutput( BigInteger.valueOf(user_value), user_addr );
          }
          catch(com.google.bitcoin.core.AddressFormatException e)
          {
              System.out.println("Invalid miner pay address: 12E9bCLYb9uzh2MHhpsyR89V3eLXZp5afr (49603174604");
          }
        }

        // Need sanity checking. If we try to pay more than we have, coinbase will be invalid.  
        // If someone we're trying to over pay, reduce everyone equally. Pool fee will be 0.
        //
        for (Map.Entry<String, Double> me : slice_map.entrySet())
        {
            try
            {
                Address user_addr = new Address(params, me.getKey());
                long user_value = (long) (me.getValue() * 100000000.0);
                BigInteger user_output = BigInteger.valueOf(user_value);

                if (user_value > 10000)
                {
                    payments_due = payments_due.add(user_output);
                }
            }
            catch(com.google.bitcoin.core.AddressFormatException e)
            {
                DecimalFormat df = new DecimalFormat("0.00000000");
                System.out.println("Invalid miner pay address: " + me.getKey() + " (" + df.format(me.getValue()) + ")");
            }
        }

        System.out.println("AddOutputs: Payments Due value: " + payments_due);

        if (payments_due.compareTo(total_value) == 1)
        {
            System.out.println("AddOutputs: Sanity check fail.");

            scale_factor = total_value.doubleValue() / payments_due.doubleValue();
            scale_factor *= .9999999;

            System.out.println("New scale_factor is " + scale_factor);
        }

        for (Map.Entry<String, Double> me : slice_map.entrySet())
        {
            try
            {
                Address user_addr = new Address(params, me.getKey());
                long user_value = (long) (me.getValue() * 100000000.0 * scale_factor);
                BigInteger user_output = BigInteger.valueOf( (long) Math.floor(user_value) );

                if (user_value > 10000)
                {
                    //System.out.println("DGM adding output address: " + me.getKey() + " (" + user_output + ")");
                    tx.addOutput( user_output, user_addr );
                    remaining = remaining.subtract(user_output);
                } else {
                    //System.out.println("Payment to " + user_addr + " skipped, too small (" + user_value + ")");
                }
            }
            catch(com.google.bitcoin.core.AddressFormatException e)
            {
                DecimalFormat df = new DecimalFormat("0.00000000");
                System.out.println("Invalid miner pay address: " + me.getKey() + " (" + df.format(me.getValue()) + ")");
            }
        }

        System.out.println("AddOutputs: Remaining after payments value: " + remaining);

        // Whatever is left over is paid to the operator
        BigInteger[] divmod = remaining.divideAndRemainder(BigInteger.valueOf(pay_to.size()));
        BigInteger per_output = divmod[0];
        BigInteger first_output = per_output.add(divmod[1]);

        boolean first=true;
        for(Address addr : pay_to)
        {
            if (first)
            {
                tx.addOutput(first_output, addr);
                System.out.println("DGM adding POOL output address: " + addr + " (" + first_output + ")");
                first=false;
            }
            else
            {
                tx.addOutput(per_output, addr);
                System.out.println("DGM adding POOL output address: " + addr + " (" + per_output + ")");
            }
        }

        //try
        //{
        //    Address user_addr = new Address(params, pu.getName());
        //    if (fee_value.compareTo(BigInteger.ZERO) > 0)
        //    {
        //        tx.addOutput(fee_value, user_addr);
        //    }
        //}
        //catch(com.google.bitcoin.core.AddressFormatException e)
        //{
        //    throw new RuntimeException(e);
        //}
    }
}
