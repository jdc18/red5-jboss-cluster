package org.red5.demos.tesla;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.infinispan.Cache;
import org.infinispan.manager.CacheContainer;
import org.infinispan.manager.EmbeddedCacheManager;
import org.jboss.as.server.CurrentServiceContainer;
import org.jboss.msc.service.ServiceName;
import org.jgroups.Channel;
import org.jgroups.Event;
import org.jgroups.PhysicalAddress;
import org.jgroups.stack.IpAddress;
import org.jgroups.util.UUID;
import org.red5.proxy.StreamingProxy;
import org.red5.server.adapter.ApplicationAdapter;
import org.red5.server.api.IConnection;
import org.red5.server.api.scope.IBasicScope;
import org.red5.server.api.scope.IBroadcastScope;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.scope.ScopeType;
import org.red5.server.api.stream.IBroadcastStream;
import org.red5.server.api.stream.IServerStream;
import org.red5.server.api.stream.IStreamListener;
import org.red5.server.api.stream.IStreamPacket;
import org.red5.server.net.rtmp.event.IRTMPEvent;
import org.red5.server.stream.message.RTMPMessage;

public class Application extends ApplicationAdapter implements IStreamListener {

	private static final long serialVersionUID = 1L;

	private IScope appScope;

	private IServerStream serverStream;

	private Map<String, StreamingProxy> streamingProxyMap = new HashMap<String, StreamingProxy>();

	@Resource(lookup = "java:jboss/infinispan/cache/micluster/default")
	private CacheContainer container;

	@Resource(lookup = "java:jboss/infinispan/cache/micluster/default")
	private EmbeddedCacheManager manager;

	@Resource(lookup = "java:jboss/infinispan/cache/server/default")
	private Cache<UUID, Date> cache;

	Collection<IpAddress> servers;

	int limiter = 1024;

	StreamingProxy streamer;

	/** {@inheritDoc} */
	@Override
	public boolean appStart(IScope app) {
		super.appStart(app);
		log.info("tesla appStart");
		System.out.println("tesla appStart");
		appScope = app;
		streamer = new StreamingProxy();
		streamer.init();
		/*streamer.setApp("tesla");
		streamer.setHost("192.168.10.2");
		streamer.setPort(1935);
		streamer.start("axisrecord" + System.currentTimeMillis() / 1000, "record", new Object[] {});*/
		// manager.start();
		return true;
	}

	public IBroadcastScope getBroadcastScope(IScope scope, String name) {
		IBasicScope basicScope = scope.getBasicScope(ScopeType.BROADCAST, name);
		if (!(basicScope instanceof IBroadcastScope)) {
			return null;
		} else {
			return (IBroadcastScope) basicScope;
		}
	}

	/** {@inheritDoc} */
	@Override
	public boolean connect(IConnection conn, IScope scope, Object[] params) {
		return true;
	}

	/** {@inheritDoc} */
	@Override
	public boolean appConnect(IConnection conn, Object[] params) {
		log.info("tesla appConnect");
		IScope appScope = conn.getScope();
		log.debug("App connect called for scope: {}", appScope.getName());
		// getting client parameters
		Map<String, Object> properties = conn.getConnectParams();
		if (log.isDebugEnabled()) {
			for (Map.Entry<String, Object> e : properties.entrySet()) {
				log.debug("Connection property: {} = {}", e.getKey(), e.getValue());
			}
		}
		getClusterMembersInfinispan();
		servers = getClusterMembersJGroups();

		return super.appConnect(conn, params);
	}

	public Collection<String> getClusterMembersInfinispan() {
		System.out.println("get getClusterMembersInfinispan");
		log.info("tesla get cluster Members 2x");

		if (manager == null) {
			log.info("cache null2");
		} else
			log.info("XXXXXXXXXXXXX cache no null");

		if (cache == null) {
			log.info("cache otro null");
		} else {
			log.info("XXXXXXXXXX cache otro no null");
		}

		if (container == null) {
			log.info("cache null");

			return null;
		} else {
			if (container.getCache() == null) {
				log.info("getCache null");
				return null;
			}
			log.info("XXXXXXXXXXXXX cache todo bien");
		}

		EmbeddedCacheManager ecm = container.getCache().getCacheManager();
		List<org.infinispan.remoting.transport.Address> members = ecm.getMembers();
		List<String> addresses = new ArrayList<String>();

		for (org.infinispan.remoting.transport.Address member : members) {
			System.out.println("Found Member:" + member.toString());
			String ipAddr = member.toString();
			addresses.add(ipAddr);
		}
		return addresses;
	}

	public Collection<IpAddress> getClusterMembersJGroups() {

		Channel channel = (Channel) CurrentServiceContainer.getServiceContainer()
				.getService(ServiceName.JBOSS.append("jgroups", "channel", "web")).getValue();

		List<org.jgroups.Address> members = channel.getView().getMembers();
		List<IpAddress> addresses = new ArrayList<IpAddress>();

		for (org.jgroups.Address member : members) {
			PhysicalAddress physicalAddr = (PhysicalAddress) channel
					.down(new Event(Event.GET_PHYSICAL_ADDRESS, member));
			IpAddress ipAddr = (IpAddress) physicalAddr;
			System.out.println("Found IP: " + ipAddr.getIpAddress().getHostAddress());
			addresses.add(ipAddr);
		}
		return addresses;
	}

	public void removeOwnIpaddress(List<IpAddress> serversAdresses) {
		try {
			List<IpAddress> localAdresses = getOwnIpAddresses();

			for (IpAddress serverAddress : serversAdresses) {
				for (IpAddress local : localAdresses) {
					System.out.println();
					if (local.getIpAddress() == localAdresses) {
						System.out.println(localAdresses.toString());
					}
				}
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public List<IpAddress> getOwnIpAddresses() throws Exception, SocketException {
		Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
		List<IpAddress> addrs = new ArrayList<IpAddress>();
		while (e.hasMoreElements()) {
			NetworkInterface n = (NetworkInterface) e.nextElement();
			Enumeration<InetAddress> ee = n.getInetAddresses();
			while (ee.hasMoreElements()) {
				InetAddress i = (InetAddress) ee.nextElement();
				IpAddress ip = new IpAddress(i.getHostAddress());
				addrs.add(ip);
				System.out.println(i.getHostAddress());
			}
		}
		return addrs;
	}

	public void streamBroadcastStart(IBroadcastStream stream) {
		IScope scope = stream.getScope();
		IBroadcastScope bsScope = getBroadcastScope(scope, stream.getPublishedName());
		StreamingProxy proxy = new StreamingProxy();
		proxy.setHost("live.justin.tv");
		proxy.setApp("app");
		proxy.setPort(1935);
		proxy.init();
		bsScope.subscribe(proxy, null);
		proxy.start("MY_STRING", StreamingProxy.KEY, null);
		streamingProxyMap.put(stream.getPublishedName(), proxy);
		stream.addStreamListener(this);
	}

	public void packetReceived(IBroadcastStream stream, IStreamPacket packet) {
		RTMPMessage m = RTMPMessage.build((IRTMPEvent) packet, packet.getTimestamp());
		try {

			limiter--;
			if (limiter > 1) {
				streamer.pushMessage(null, m);
			} else {
				if (streamer != null) {
					stream.removeStreamListener(this);
					streamer.stop();
					streamer = null;
				}
			}

		} catch (IOException e) {

			e.printStackTrace();
		}
	}

	synchronized public void streamBroadcastClose(IBroadcastStream stream) {
		StreamingProxy proxy = streamingProxyMap.get(stream.getPublishedName());
		if (proxy != null) {
			proxy.stop();
			IScope scope = stream.getScope();
			IBroadcastScope bsScope = getBroadcastScope(scope, stream.getPublishedName());
			if (bsScope != null) {
				bsScope.unsubscribe(proxy);
			}
		}
	}

	/** {@inheritDoc} */
	@Override
	public void appDisconnect(IConnection conn) {
		log.info("tesla appDisconnect");
		if (appScope == conn.getScope() && serverStream != null) {
			serverStream.close();
		}
		super.appDisconnect(conn);
	}
}
