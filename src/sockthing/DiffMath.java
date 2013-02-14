package sockthing;
import java.math.BigInteger;
import com.google.bitcoin.core.Sha256Hash;

public class DiffMath
{
    public static Sha256Hash getTargetForDifficulty(int diff)
    {
        BigInteger two = BigInteger.valueOf(2);
        BigInteger diff_one = two.pow(256-32);

        BigInteger diff_target = diff_one.divide(BigInteger.valueOf(diff));

        String target = diff_target.toString(16).toLowerCase();
        while(target.length() < 64)
        {
            target = "0" + target;
        }
        return new Sha256Hash(target);
    }


    private static void printDiff(int diff)
    {
        String diff_str = "" + diff;
        while (diff_str.length() < 10) diff_str+=" ";
        System.out.println(" " + diff_str + " - " + getTargetForDifficulty(diff));
    }

    public static void main(String args[])
    {
        printDiff(1);
        printDiff(2);
        printDiff(3);
        printDiff(4);
        printDiff(32);
        printDiff(65536);
    }
}
