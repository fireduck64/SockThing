package sockthing;

import java.util.Properties;
import java.io.FileInputStream;

import java.util.StringTokenizer;
import java.util.LinkedList;
import java.util.List;

public class Config
{
    private Properties props;

    public Config(String file_name)
        throws java.io.IOException
    {
        props = new Properties();

        props.load(new FileInputStream(file_name));
    }

    public void require(String key)
    {
        if (!props.containsKey(key))
        {
            throw new RuntimeException("Missing required key: " + key);
        }
    }

    public String get(String key)
    {
        return props.getProperty(key);
    }
    public int getInt(String key)
    {
        return Integer.parseInt(get(key));
    }

    public List<String> getList(String key)
    {
        String big_str = get(key);

        StringTokenizer stok = new StringTokenizer(big_str, ",");

        LinkedList<String> lst = new LinkedList<String>();
        while(stok.hasMoreTokens())
        {
            String node = stok.nextToken().trim();
            lst.add(node);
        }
        return lst;
    }
    

}
