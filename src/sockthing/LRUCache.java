package sockthing;


import java.util.LinkedHashMap;
import java.util.Map;

public class LRUCache<K,V> extends LinkedHashMap<K,V>
{

	private static final long serialVersionUID=9L;
	private int MAX_CAP;

	public LRUCache(int cap)
	{
		super(cap*2, 0.75f, true); 
		MAX_CAP=cap;

	}

	protected boolean removeEldestEntry(Map.Entry<K,V> eldest)
	{
		return (size() > MAX_CAP);

	}

}
