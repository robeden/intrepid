package com.starlight.intrepid;

import junit.framework.TestCase;
import com.starlight.intrepid.ProxyLeaseInfo;
import com.starlight.intrepid.VMID;

import java.util.UUID;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.TimeUnit;


/**
 *
 */
public class ProxyLeaseInfoTest extends TestCase {
	public void testDelayQueueOperation() throws Exception {
		DelayQueue<ProxyLeaseInfo> q = new DelayQueue<ProxyLeaseInfo>();

		VMID vmid = new VMID( UUID.randomUUID(), "test vmid" );

		q.add( new ProxyLeaseInfo( vmid, 1, 1000 ) );
		q.add( new ProxyLeaseInfo( vmid, 2, 500 ) );
		q.add( new ProxyLeaseInfo( vmid, 3, 3000 ) );
		q.add( new ProxyLeaseInfo( vmid, 4, 10 ) );

		// First lease should be right away, so all PLI's should come out immediately
		long start = System.currentTimeMillis();
		ProxyLeaseInfo pli1 = null;
		ProxyLeaseInfo pli2 = null;
		ProxyLeaseInfo pli3 = null;
		ProxyLeaseInfo pli4 = null;
		while( !q.isEmpty() ) {
			ProxyLeaseInfo pli = q.poll( 1, TimeUnit.SECONDS );
			assertNotNull( pli );

			switch( pli.object_id ) {
				case 1:
					assertNull( pli1 );
					pli1 = pli;
					break;
				case 2:
					assertNull( pli2 );
					pli2 = pli;
					break;
				case 3:
					assertNull( pli3 );
					pli3 = pli;
					break;
				case 4:
					assertNull( pli4 );
					pli4 = pli;
					break;
				default:
					fail( "Unknown OID: " + pli.object_id );
			}
		}

		long duration = System.currentTimeMillis() - start;
		assertTrue( String.valueOf( duration ), duration < 1000 );

		assertNotNull( pli1 );
		assertNotNull( pli2 );
		assertNotNull( pli3 );
		assertNotNull( pli4 );

		// Now make sure leases come out in expected time

		q.add( pli1.next() );
		q.add( pli2.next() );
		q.add( pli3.next() );
		q.add( pli4.next() );

		ProxyLeaseInfo pli = q.poll( 50, TimeUnit.MILLISECONDS );
		assertNotNull( pli );
		assertEquals( 4, pli.object_id );

		pli = q.poll( 500, TimeUnit.MILLISECONDS );
		assertNotNull( pli );
		assertEquals( 2, pli.object_id );

		pli = q.poll( 500, TimeUnit.MILLISECONDS );
		assertNotNull( pli );
		assertEquals( 1, pli.object_id );

		pli = q.poll( 2000, TimeUnit.MILLISECONDS );
		assertNotNull( pli );
		assertEquals( 3, pli.object_id );
	}
}
