package com.starlight.intrepid.gui;

import ca.odell.glazedlists.BasicEventList;
import ca.odell.glazedlists.EventList;
import ca.odell.glazedlists.SortedList;
import ca.odell.glazedlists.gui.AdvancedTableFormat;
import ca.odell.glazedlists.swing.DefaultEventTableModel;
import com.starlight.intrepid.Intrepid;
import com.starlight.intrepid.IntrepidSetup;
import com.starlight.intrepid.PerformanceListener;
import com.starlight.intrepid.VMID;
import com.starlight.intrepid.auth.UserContextInfo;
import com.starlight.intrepid.message.IMessage;
import com.starlight.thread.SharedThreadPool;
import com.starlight.ui.UIKit;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.text.DateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;


/**
 * A panel that shows the connections active in an Intrepid instance.
 */
public class LeaseDebugPane extends JPanel implements PerformanceListener {
	private static final AtomicLong REUSED_ATOMIC_LONG = new AtomicLong();

	private final JTable table;

	private final Intrepid instance;
	private final EventList<LeaseInfoContainer> root_list;
	private final EventList<LeaseInfoContainer> sort_list;

	private final TIntObjectMap<LeaseInfoContainer> info_map;

	private final Timer repaint_timer = new Timer( 5000, new RepaintActionListener() );


	public LeaseDebugPane( Intrepid instance ) {
		this.instance = instance;

		root_list = new BasicEventList<LeaseInfoContainer>();
		sort_list = new SortedList<LeaseInfoContainer>( root_list );
		info_map = new TIntObjectHashMap<LeaseInfoContainer>();

		instance.addPerformanceListener( this );

		table = new JTable( new DefaultEventTableModel<LeaseInfoContainer>(
			sort_list, new LeaseTableFormat() ) );
		table.getColumnModel().getColumn( 3 ).setCellRenderer( new DurationRenderer() );
		table.getColumnModel().getColumn( 4 ).setCellRenderer( new DurationRenderer() );
		UIKit.autosizeTableColumn( table, 1, "-2147483647", true, 2 );
		UIKit.autosizeTableColumn( table, 3, "0000:00:00", true, 2 );
		UIKit.autosizeTableColumn( table, 4, "0000:00:00", true, 2 );
		UIKit.autosizeTableColumn( table, 5, Boolean.TRUE, true, 2 );
		UIKit.autosizeTableColumn( table, 6, new AtomicLong( 999 ), true, 2 );

		setLayout( new BorderLayout() );

		add( new JScrollPane( table ), BorderLayout.CENTER );

		repaint_timer.start();
	}


	public void dispose() {
		instance.removePerformanceListener( this );

		repaint_timer.stop();

		sort_list.dispose();
	}


	@Override
	public void leaseInfoUpdated( final VMID vmid, final int object_id,
		final String delegate_tostring, final boolean holding_strong_reference,
		final int leasing_vm_count, final boolean renew, final boolean release ) {

		if ( !SwingUtilities.isEventDispatchThread() ) {
			SwingUtilities.invokeLater( new Runnable() {
				@Override
				public void run() {
					leaseInfoUpdated( vmid, object_id, delegate_tostring,
						holding_strong_reference, leasing_vm_count, renew, release );
				}
			} );
			return;
		}

		LeaseInfoContainer info = info_map.get( object_id );
		if ( info == null ) {
			info = new LeaseInfoContainer( vmid, object_id, delegate_tostring,
				holding_strong_reference, leasing_vm_count );
			info_map.put( object_id, info );
			root_list.add( info );
			repaint();
			return;
		}

		info.holding_strong_ref = holding_strong_reference;
		info.leasing_vm_count = leasing_vm_count;
		if ( renew ) info.last_lease_renew = System.currentTimeMillis();
		repaint();
	}

	@Override
	public void leasedObjectRemoved( final VMID vmid, final int object_id ) {
		if ( !SwingUtilities.isEventDispatchThread() ) {
			SwingUtilities.invokeLater( new Runnable() {
				@Override
				public void run() {
					leasedObjectRemoved( vmid, object_id );
				}
			} );
			return;
		}

		final LeaseInfoContainer info = info_map.get( object_id );
		if ( info == null ) return;


		// Schedule the info to be removed in 15 seconds.
		SharedThreadPool.INSTANCE.schedule( new Runnable() {
			@Override
			public void run() {
				if ( !SwingUtilities.isEventDispatchThread() ) {
					SwingUtilities.invokeLater( this );
					return;
				}

				sort_list.remove( info );
				info_map.remove( info.object_id );
			}
		}, 15, TimeUnit.SECONDS );

	}

	private static class LeaseInfoContainer
		implements Comparable<LeaseInfoContainer> {

		private final VMID vmid;
		private final int object_id;
		private final String delegate_tostring;
		private final long start_time;

		private boolean holding_strong_ref;
		private int leasing_vm_count;
		private long last_lease_renew;

		LeaseInfoContainer( VMID vmid, int object_id, String delegate_tostring,
			boolean holding_strong_ref, int leasing_vm_count ) {

			this.vmid = vmid;
			this.object_id = object_id;
			this.delegate_tostring = delegate_tostring;
			this.start_time = System.currentTimeMillis();

			this.holding_strong_ref = holding_strong_ref;
			this.leasing_vm_count = leasing_vm_count;
			this.last_lease_renew = start_time;
		}

		@Override
		public int compareTo( LeaseInfoContainer o ) {
			if ( start_time < o.start_time ) return -1;
			else if ( start_time == o.start_time ) return 0;
			else return 1;
		}
	}


	private class LeaseTableFormat
		implements AdvancedTableFormat<LeaseInfoContainer> {

		@Override
		public Class getColumnClass( int column ) {
			switch( column ) {
				case 0:
					return VMID.class;
				case 1:
					return AtomicLong.class;
				case 2:
					return String.class;
				case 3:
					return AtomicLong.class;
				case 4:
					return AtomicLong.class;
				case 5:
					return Boolean.class;
				case 6:
					return AtomicLong.class;
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
			return 7;
		}

		@Override
		public String getColumnName( int column ) {
			switch( column ) {
				case 0:
					return Resources.HEADER_VMID.getValue();
				case 1:
					return Resources.HEADER_OBJECT_ID.getValue();
				case 2:
					return Resources.HEADER_DELEGATE_TO_STRING.getValue();
				case 3:
					return Resources.HEADER_DURATION.getValue();
				case 4:
					return Resources.HEADER_LAST_LEASE_RENEW.getValue();
				case 5:
					return Resources.HEADER_STRONG_REF.getValue();
				case 6:
					return Resources.HEADER_LEASING_VM_COUNT.getValue();
				default:
					assert false : "Unknown column: " + column;
					return "Unknown: " + column;
			}
		}

		@Override
		public Object getColumnValue( LeaseInfoContainer info, int column ) {
			assert SwingUtilities.isEventDispatchThread();

			switch( column ) {
				case 0:
					return info.vmid;
				case 1:
					return setAndReturn( info.object_id );
				case 2:
					return info.delegate_tostring;
				case 3:
					return setAndReturn( info.start_time );
				case 4:
					return setAndReturn( info.last_lease_renew );
				case 5:
					return Boolean.valueOf( info.holding_strong_ref );
				case 6:
					return setAndReturn( info.leasing_vm_count );
				default:
					assert false : "Unknown column: " + column;
					return null;
			}
		}

		private AtomicLong setAndReturn( long value ) {
			REUSED_ATOMIC_LONG.set( value );
			return REUSED_ATOMIC_LONG;
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

			LeaseDebugPane pane = new LeaseDebugPane( instance );
			UIKit.testComponent( pane );
		}
		else if ( args[ 0 ].equalsIgnoreCase( "try_client" ) ) {
			int port = Integer.parseInt( args[ 1 ] );

			Intrepid instance = Intrepid.create( null );

			LeaseDebugPane pane = new LeaseDebugPane( instance );
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


	@Override
	public void inboundRemoteCallCompleted( VMID instance_vmid, long time, int call_id,
		Object result, boolean result_was_thrown ) {}

	@Override
	public void remoteCallStarted( VMID instance_vmid, long time, int call_id,
		VMID destination_vmid, int object_id, int method_id, Method method, Object[] args,
		UserContextInfo user_context, String persistent_name ) {}

	@Override
	public void remoteCallCompleted( VMID instance_vmid, long time, int call_id,
		Object result, boolean result_was_thrown, Long server_time ) {}

	@Override
	public void inboundRemoteCallStarted( VMID instance_vmid, long time, int call_id,
		VMID source_vmid, int object_id, int method_id, Method method, Object[] args,
		UserContextInfo user_context, String persistent_name ) {}

	@Override
	public void virtualChannelOpened( VMID instance_vmid, VMID peer_vmid,
		short channel_id ) {}

	@Override
	public void virtualChannelClosed( VMID instance_vmid, VMID peer_vmid,
		short channel_id ) {}

	@Override
	public void virtualChannelDataReceived( VMID instance_vmid, VMID peer_vmid,
		short channel_id, int bytes ) {}

	@Override
	public void virtualChannelDataSent( VMID instance_vmid, VMID peer_vmid,
		short channel_id, int bytes ) {}

	@Override
	public void messageSent( VMID destination_vmid, IMessage message ) {}

	@Override
	public void messageReceived( VMID source_vmid, IMessage message ) {}
}
