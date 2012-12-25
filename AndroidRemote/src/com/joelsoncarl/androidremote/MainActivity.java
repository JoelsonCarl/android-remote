package com.joelsoncarl.androidremote;

import android.app.ActionBar;
import android.app.Activity;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.view.Menu;

public class MainActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main_volume);
		
		final ActionBar actionBar = getActionBar();

	    // Specify that tabs should be displayed in the action bar.
	    actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

	    // Create a tab listener that is called when the user changes tabs.
	    ActionBar.TabListener tabListener = new ActionBar.TabListener() {
	        public void onTabSelected(ActionBar.Tab tab,
	                FragmentTransaction ft) {
	        	if (tab.getText().toString().compareTo(
	        			getResources().getString(R.string.volume_tab_name)) == 0) {
	        		setContentView(R.layout.activity_main_volume);
	        	}
	        	else if (tab.getText().toString().compareTo(
	        			getResources().getString(R.string.mouse_tab_name)) == 0) {
	        		setContentView(R.layout.activity_main_mouse);
	        	}
	        	else if (tab.getText().toString().compareTo(
	        			getResources().getString(R.string.connect_tab_name)) == 0) {
	        		setContentView(R.layout.activity_main_connect);
	        	}
	        }

	        public void onTabUnselected(ActionBar.Tab tab,
	                FragmentTransaction ft) { }

	        public void onTabReselected(ActionBar.Tab tab,
	                FragmentTransaction ft) { }
	    };

	    // Add tabs
	    actionBar.addTab(actionBar.newTab().setText("Volume").setTabListener(tabListener));
	    actionBar.addTab(actionBar.newTab().setText("Mouse").setTabListener(tabListener));
	    actionBar.addTab(actionBar.newTab().setText("Connect").setTabListener(tabListener));

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

}
