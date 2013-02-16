package com.joelsoncarl.androidremote;

import java.io.IOException;
import java.net.Socket;

import android.app.ActionBar;
import android.app.Activity;
import android.app.FragmentTransaction;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.TextView;

/**
 * The main Activity
 */
public class MainActivity extends Activity {

    /** The RFB Client handler */
    private RfbClient m_rfbClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_volume);

        m_rfbClient = new RfbClient(this);

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
                    findViewById(R.id.mouse_left_button).setOnTouchListener(new MouseTouchListener());
                    findViewById(R.id.mouse_right_button).setOnTouchListener(new MouseTouchListener());
                }
                else if (tab.getText().toString().compareTo(
                        getResources().getString(R.string.connect_tab_name)) == 0) {
                    setContentView(R.layout.activity_main_connect);
                    m_rfbClient.m_connectMsg = (TextView) findViewById(R.id.connection_message);
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
    
    /**
     * Called when the "Connect" button is pressed on the Connection Settings tab
     * @param view
     */
    public void rfbConnect(View view) {
        new RfbConnectTask().execute();
    }

    /**
     * Called when the "Disconnect" button is pressed on the Connection Settings tab
     * @param view
     */
    public void rfbDisconnect(View view) {
        m_rfbClient.closeConnection();
    }

    /**
     * Establishes the socket connection to the RFB Server
     */
    private class RfbConnectTask extends AsyncTask<Void, String, Socket> {
        protected Socket doInBackground(Void... voids) {
            Socket rfbServerSock = null;
            try {
                publishProgress(getResources().getString(R.string.connection_progress));
                rfbServerSock = new Socket("192.168.13.2", 5901);
            }
            catch (IOException e) {
                m_rfbClient.m_connectMsg.setText("RFB Socket Connection Error");
            }
            return rfbServerSock;
        }

        protected void onProgressUpdate(String... progress) {
            m_rfbClient.m_connectMsg.setText(progress[0]);
        }

        protected void onPostExecute(Socket sock) {
            m_rfbClient.connectDone(sock);
        }
    }
    
    /**
     * The OnTouchListener for the mouse buttons
     */
    private class MouseTouchListener implements OnTouchListener {
        @Override
        public boolean onTouch(View view, MotionEvent event) {
            // Determine which button was pressed
            int button;
            if (view.getId() == R.id.mouse_left_button) {
                button = RfbClient.LEFT_BUTTON;
            }
            else {
                button = RfbClient.RIGHT_BUTTON;
            }
            // Determine if button is pressed or released and
            // act accordingly
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                view.setPressed(true);
                m_rfbClient.mouseEvent(button, true, 0, 0);
            }
            else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                view.setPressed(false);
                m_rfbClient.mouseEvent(button, false, 0, 0);
            }
            return true;
        }
    }

}
