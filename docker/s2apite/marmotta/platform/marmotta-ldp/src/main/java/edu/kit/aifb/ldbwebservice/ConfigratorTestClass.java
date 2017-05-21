package edu.kit.aifb.ldbwebservice;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class ConfigratorTestClass {

	static String hostname;

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		ConfigratorTestClass config = new ConfigratorTestClass();
		String myhost = config.getLocalHostName();

	}


	private String getLocalHostName(){
		if(hostname == null){
			try
			{
				InetAddress addr;
				addr = InetAddress.getLocalHost();
				hostname = addr.getHostName();
			}
			catch (UnknownHostException ex)
			{
				System.out.println("Hostname can not be resolved");
			}
		}
		return hostname;
	}
}
