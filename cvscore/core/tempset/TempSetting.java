package twcore.core.tempset;


abstract class TempSetting
{
	protected String m_name;

	public TempSetting(String name)
	{
		m_name = name;
	}

	public String getName()
	{
		return m_name;
	}

	public abstract Object getValue();
	public abstract Result setValue(String arg);
}