package sockthing;

import java.util.Properties;
import java.io.FileInputStream;


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

}
