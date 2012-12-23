package com.starlight.intrepid.gui;

import ca.odell.glazedlists.*;
import ca.odell.glazedlists.gui.AdvancedTableFormat;
import ca.odell.glazedlists.swing.DefaultEventTableModel;
import com.starlight.intrepid.ConnectionListener;
import com.starlight.intrepid.Intrepid;
import com.starlight.intrepid.IntrepidSetup;
import com.starlight.intrepid.VMID;
import com.starlight.intrepid.auth.ConnectionArgs;
import com.starlight.intrepid.auth.UserContextInfo;
import com.starlight.thread.SharedThreadPool;
import com.starlight.ui.UIKit;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.text.DateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;


/**
 * A panel that shows the connections active in an Intrepid instance.
 */
public class ConnectionDebugPane extends JPanel implements ConnectionListener {
	private static final AtomicLong REUSED_ATOMIC_LONG = new AtomicLong();

	private final JTable table;

	private final Intrepid instance;
	private final EventList<ConnectionInfoContainer> root_list;
	private final EventList<ConnectionInfoContainer> sort_list;

	private final DisposableMap<InetSocketAddress,ConnectionInfoContainer> info_map;

	private final Timer repaint_timer = new Timer( 5000, new RepaintActionListener() );


	public ConnectionDebugPane( Intrepid instance ) {
		this.instance = instance;

		root_list = new BasicEventList<ConnectionInfoContainer>();
		sort_list = new SortedList<ConnectionInfoContainer>( root_list );
		info_map = GlazedLists.syncEventListToMap( root_list,
			new FunctionList.Function<ConnectionInfoContainer, InetSocketAddress>() {
				@Override
				public InetSocketAddress evaluate( ConnectionInfoContainer info ) {
					return info.socket_address;
				}
			} );

		instance.addConnectionListener( this );

		table = new JTable( new DefaultEventTableModel<ConnectionInfoContainer>(
			sort_list, new ConnectionTableFormat() ) );
		table.setDefaultRenderer( AtomicLong.class, new DurationRenderer() );
		table.setDefaultRenderer( InetSocketAddress.class,
			new InetSocketAddressRenderer() );
		UIKit.autosizeTableColumn( table, 0, "000.000.000.000:00000", true, 2 );
		UIKit.autosizeTableColumn( table, 2, "0000:00:00", true, 2 );
		UIKit.autosizeTableColumn( table, 3, ConnectionState.RECONNECTING, true, 2 );
		UIKit.autosizeTableColumn( table, 4, "SSL/Compress", true, 2 );

		setLayout( new BorderLayout() );

		add( new JScrollPane( table ), BorderLayout.CENTER );

		repaint_timer.start();
	}


	public void dispose() {
		instance.removeConnectionListener( this );

		repaint_timer.stop();

		info_map.dispose();
		sort_list.dispose();
	}


	@Override
	public void connectionClosed( final InetAddress host, final int port,
		final VMID source_vmid, final VMID vmid, final Object attachment,
		final boolean will_attempt_reconnect ) {

		if ( !SwingUtilities.isEventDispatchThread() ) {
			SwingUtilities.invokeLater( new Runnable() {
				@Override
				public void run() {
					connectionClosed( host, port, source_vmid, vmid, attachment,
						will_attempt_reconnect );
				}
			} );
			return;
		}

		InetSocketAddress address = new InetSocketAddress( host, port );

		final ConnectionInfoContainer info = info_map.get( address );
		if ( info == null ) return;

		if ( will_attempt_reconnect ) info.state = ConnectionState.RECONNECTING;
		else {
			info.state = ConnectionState.CLOSED;

			// Schedule the info to be removed in 15 seconds.
			SharedThreadPool.INSTANCE.schedule( new Runnable() {
				@Override
				public void run() {
					if ( !SwingUtilities.isEventDispatchThread() ) {
						SwingUtilities.invokeLater( this );
						return;
					}

					sort_list.remove( info );
				}
			}, 15, TimeUnit.SECONDS );
		}

		table.repaint();
	}

	@Override
	public void connectionOpened( final InetAddress host, final int port,
		final Object attachment, final VMID source_vmid, final VMID vmid,
		final UserContextInfo user_context, final VMID previous_vmid,
		final Object connection_type_description ) {

		if ( !SwingUtilities.isEventDispatchThread() ) {
			SwingUtilities.invokeLater( new Runnable() {
				@Override
				public void run() {
					connectionOpened( host, port, attachment, source_vmid, vmid,
						user_context,
						previous_vmid, connection_type_description );
				}
			} );
			return;
		}

		InetSocketAddress address = new InetSocketAddress( host, port );

		ConnectionInfoContainer info = info_map.get( address );
		if ( info == null ) {
			info = new ConnectionInfoContainer();
			info.socket_address = new InetSocketAddress( host, port );

			root_list.add( info );
		}

		info.vmid = vmid;
		info.state = ConnectionState.OK;
		info.user_context = user_context;
		info.type_desc = connection_type_description;

		table.repaint();
	}

	@Override
	public void connectionOpenFailed( final InetAddress host, final int port,
		final Object attachment, final Exception error, final boolean will_retry ) {

		if ( !SwingUtilities.isEventDispatchThread() ) {
			SwingUtilities.invokeLater( new Runnable() {
				@Override
				public void run() {
					connectionOpenFailed( host, port, attachment, error, will_retry );
				}
			} );
			return;
		}

		InetSocketAddress address = new InetSocketAddress( host, port );
		final ConnectionInfoContainer info = info_map.get( address );
		if ( info == null ) return;

		if ( will_retry ) {
			info.state = ConnectionState.CONNECTING;  // Leave(?) at connecting
		}
		else {
			info.state = ConnectionState.CLOSED;

			// Schedule the info to be removed in 15 seconds.
			SharedThreadPool.INSTANCE.schedule( new Runnable() {
				@Override
				public void run() {
					if ( !SwingUtilities.isEventDispatchThread() ) {
						SwingUtilities.invokeLater( this );
						return;
					}

					sort_list.remove( info );
				}
			}, 15, TimeUnit.SECONDS );
		}

		table.repaint();
	}

	@Override
	public void connectionOpening( final InetAddress host, final int port,
		final Object attachment, final ConnectionArgs args,
		final Object connection_type_description ) {

		if ( !SwingUtilities.isEventDispatchThread() ) {
			SwingUtilities.invokeLater( new Runnable() {
				@Override
				public void run() {
					connectionOpening( host, port, attachment, args,
						connection_type_description );
				}
			} );
			return;
		}


		InetSocketAddress address = new InetSocketAddress( host, port );

		ConnectionInfoContainer info = info_map.get( address );
		if ( info != null ) {
			info.state = ConnectionState.CONNECTING;
			table.repaint();
			return;
		}

		info = new ConnectionInfoContainer();
		info.socket_address = address;
		info.state = ConnectionState.CONNECTING;
		info.type_desc = connection_type_description;

		root_list.add( info );
		repaint();
	}


	private static enum ConnectionState {
		CONNECTING,
		OK,
		RECONNECTING,
		CLOSED
	}

	private static class ConnectionInfoContainer
		implements Comparable<ConnectionInfoContainer> {

		private InetSocketAddress socket_address;
		private VMID vmid;
		private long start_time;
		private UserContextInfo user_context;
		private ConnectionState state;
		private Object type_desc;

		ConnectionInfoContainer() {
			start_time = System.currentTimeMillis();
		}

		@Override
		public int compareTo( ConnectionInfoContainer o ) {
			if ( start_time < o.start_time ) return -1;
			else if ( start_time == o.start_time ) return 0;
			else return 1;
		}
	}


	private class ConnectionTableFormat
		implements AdvancedTableFormat<ConnectionInfoContainer> {

		@Override
		public Class getColumnClass( int column ) {
			switch( column ) {
				case 0:
					return InetSocketAddress.class;
				case 1:
					return VMID.class;
				case 2:
					return AtomicLong.class;
				case 3:
					return ConnectionState.class;
				case 4:
				case 5:
					return String.class;
				default:
					assert false : "Unknown column: " + column;
					return Object.class;
			}
		}

		@Override
		public Comparator getColumnComparator( int column ) {
			return null;
		}

		@Override
		public int getColumnCount() {
			return 5;
		}

		@Override
		public String getColumnName( int column ) {
			switch( column ) {
				case 0:
					return Resources.HEADER_ADDRESS.getValue();
				case 1:
					return Resources.HEADER_VMID.getValue();
				case 2:
					return Resources.HEADER_DURATION.getValue();
				case 3:
					return Resources.HEADER_STATUS.getValue();
				case 4:
					return Resources.HEADER_TYPE.getValue();
				case 5:
					return Resources.HEADER_USER.getValue();
				default:
					assert false : "Unknown column: " + column;
					return "Unknown: " + column;
			}
		}

		@Override
		public Object getColumnValue( ConnectionInfoContainer info, int column ) {
			assert SwingUtilities.isEventDispatchThread();

			switch( column ) {
				case 0:
					return info.socket_address;
				case 1:
					return info.vmid;
				case 2:
					REUSED_ATOMIC_LONG.set( info.start_time );
					return REUSED_ATOMIC_LONG;
				case 3:
					return info.state;
				case 4:
					return info.type_desc == null ?
						null : String.valueOf(  info.type_desc );
				case 5:
					UserContextInfo context = info.user_context;
					if ( context == null ) return null;
					else return context.getUserName();
				default:
					assert false : "Unknown column: " + column;
					return null;
			}
		}
	}


	private class DurationRenderer extends DefaultTableCellRenderer {
		private final DateFormat tooltip_formatter = DateFormat.getDateTimeInstance();
		private final Date reused_date = new Date();

		@Override
		public Component getTableCellRendererComponent( JTable table, Object value,
			boolean isSelected, boolean hasFocus, int row, int column ) {

			String tooltip = null;
			if ( value instanceof Number ) {
				long start_time = ( ( Number ) value ).longValue();
				reused_date.setTime( start_time );

				tooltip = tooltip_formatter.format( reused_date );

				long duration = System.currentTimeMillis() - start_time;
				value = toDurationString( duration );
			}

			JComponent component =
				( JComponent ) super.getTableCellRendererComponent( table, value,
				isSelected, hasFocus, row, column );

			if ( tooltip != null ) component.setToolTipText( tooltip );

			return component;
		}

		private String toDurationString( long duration ) {
			long time = duration / 1000;
			String seconds = Integer.toString( ( int ) ( time % 60 ) );
			String minutes = Integer.toString( ( int ) ( ( time % 3600 ) / 60 ) );
			String hours = Integer.toString( ( int ) ( time / 3600 ) );
			for ( int i = 0; i < 2; i++ ) {
				if ( seconds.length() < 2 ) {
					seconds = "0" + seconds;
				}
				if ( minutes.length() < 2 ) {
					minutes = "0" + minutes;
				}
				if ( hours.length() < 2 ) {
					hours = "0" + hours;
				}
			}
			return new StringBuilder( hours ).append( ':' ).append( minutes ).append(
				':' ).append( seconds ).toString();
		}
	}


	private class InetSocketAddressRenderer extends DefaultTableCellRenderer {
		@Override
		public Component getTableCellRendererComponent( JTable table, Object value,
			boolean isSelected, boolean hasFocus, int row, int column ) {

			if ( value != null && value instanceof InetSocketAddress ) {
				InetSocketAddress sock_address = ( InetSocketAddress ) value;
				StringBuilder buf =
					new StringBuilder( sock_address.getAddress().getHostAddress() );
				buf.append( ':' );
				buf.append( sock_address.getPort() );
				value = buf.toString();
			}
			return super.getTableCellRendererComponent( table, value, isSelected,
				hasFocus, row, column );
		}
	}


	private class RepaintActionListener implements ActionListener {
		@Override
		public void actionPerformed( ActionEvent e ) {
			table.repaint();
		}
	}


	public static void main( String[] args ) throws Exception {
		if ( args.length == 0 ) {
			System.out.println( "Usage: ConnectionDebugPane server" );
			System.out.println( "                           client <port>" );
			System.out.println( "                           try_client <port>" );
			return;
		}

		if ( args[ 0 ].equalsIgnoreCase( "server" ) ) {
			Intrepid instance = Intrepid.create( new IntrepidSetup().openServer() );
			System.out.println( "Server listening on port " + instance.getServerPort() );

			ConnectionDebugPane pane = new ConnectionDebugPane( instance );
			UIKit.testComponent( pane );
		}
		else if ( args[ 0 ].equalsIgnoreCase( "try_client" ) ) {
			int port = Integer.parseInt( args[ 1 ] );

			Intrepid instance = Intrepid.create( null );

			ConnectionDebugPane pane = new ConnectionDebugPane( instance );
			UIKit.testComponent( pane );

			System.out.println( "Connecting to server..." );

			instance.tryConnect( InetAddress.getLocalHost(), port, null, null,
				10, TimeUnit.MINUTES );
		}
		else {
			int port = Integer.parseInt( args[ 1 ] );

			Intrepid instance = Intrepid.create( null );

			System.out.println( "Connecting to server..." );

			instance.connect( InetAddress.getLocalHost(), port, null, null );
		}
	}
}
