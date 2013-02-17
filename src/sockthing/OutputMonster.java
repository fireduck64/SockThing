
package sockthing;

import com.google.bitcoin.core.Transaction;

import java.math.BigInteger;


public interface OutputMonster
{
    public void addOutputs(PoolUser pu, Transaction tx, BigInteger total_value, BigInteger fee_value);
}
