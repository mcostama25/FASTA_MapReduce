package es.upm.dit.cnvr_fcon.FASTAMapReduce;

//import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.KeyException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

import com.fasterxml.jackson.databind.ObjectMapper;

import es.upm.dit.cnvr_fcon.FASTA_aux.Busqueda;
import es.upm.dit.cnvr_fcon.FASTA_aux.FASTABuscar;
import es.upm.dit.cnvr_fcon.ZK.CreateSession;
import es.upm.dit.cnvr_fcon.ZK.CreateZNode;

/**
 * @author mmiguel, aalonso
 * @since   2023-03-20 
 */
public class FASTAProcess implements Watcher{

	private static String nodeMember  = "/members";
	private static String nodeAMember = "/member-";
	private String myId;
	private String myName;
	private String nodeComm     = "/comm";
	private String nodeSegment  = "/segments";
	private String nodeASegment = "/segment-";
	private String nodeResult   = "/results";
	// private String nodeAResult  = "/result-";
	private ZooKeeper zk        = null;


	
	static {
		System.setProperty("java.util.logging.SimpleFormatter.format",
				"[%1$tF %1$tT][%4$-7s] [%5$s] [%2$-7s] %n");
	}

	static final Logger LOGGER = Logger.getLogger(FASTAMapReduce.class.getName());


	public FASTAProcess () {
		configurarLogger();
		create_ZK_Nodes();
		getSegment();
	}

	private void create_ZK_Nodes(){
		//Create Session to zk
		String[] hosts = {"127.0.0.1:2181", "127.0.0.1:2181", "127.0.0.1:2181"};
		CreateSession cs = new CreateSession();
		ZooKeeper zk = cs.ConnectSession(hosts);
		//TODO: Create the zkNodes required and set watchers
		if (zk != null) {
			//Create node /members
			CreateZNode zMember = new CreateZNode(zk, nodeMember, new byte[0], CreateMode.EPHEMERAL_SEQUENTIAL);
			//Create node /comm
			CreateZNode zComm = new CreateZNode(zk, nodeComm, new byte[0], CreateMode.PERSISTENT);
			watcherCommMember(nodeComm); // levantamos un wacther para el path /Comm
		}
	}

	/**
	 * Configuracion de un logger
	 */
	private void configurarLogger() {
		//Configurar un handler
		ConsoleHandler handler;
		handler = new ConsoleHandler(); 
		handler.setLevel(Level.FINEST); 
		LOGGER.addHandler(handler); 
		LOGGER.setLevel(Level.FINEST);
	}

	// Notified when the number of children in /comm/memberxx is updated
	private void  watcherCommMember(String path) { // este wacher se levanta al crearse el nodo /comm y va estar monitorizando la cracion y destruccion de hijos.
		try {
			zk.exists(path, new Watcher() {
				@Override
				public void process(WatchedEvent event) {
					// TODO: process for getting and handling segments 
					if (event.getType() == Event.EventType.NodeCreated) {
						LOGGER.info("Nuevo nodo en:" + path);
						getSegment(); // cuando se crea un nuevo nodo se lanza la fuincion getSegment().
					} else if (event.getType() == Event.EventType.NodeDeleted) {
						LOGGER.info("Nodo eliminado: " + path);
					}
					watcherCommMember(path); // volvemos a levantar el Watcher.
				}
			});
		} catch (KeeperException | InterruptedException e) {
			LOGGER.severe("Error seting node creation watcher: " + e.getMessage());
		}
	};

	private void getSegment(){
		// TODO: How to get a segment from /comm/member-xx/segment
		try {
			List<String> members = zk.getChildren(nodeComm, false); // get the children of node Comm (get the members)
			if (!members.isEmpty()) { 
				for (String member : members) { // for each member, get the children (get the segment)
					List<String> segment = zk.getChildren(member, false); // children of the member x
					String segmentPath = nodeComm + "/" + member; //este path es /comm/member-xx el nodo segmento se añade luego.
					processSegment(segmentPath);
				}	
			}
		} catch (KeeperException | InterruptedException e) {
			LOGGER.severe("Error getting segment: " + e.getMessage());
		}
	}
	
	@Override
	public void process(WatchedEvent event) {
		try {
			System.out.println("Unexpected invocated this method. Process of the object");
			List<String> list = zk.getChildren(nodeSegment, null);//watcherSegment); //this);
			printListMembers(list);

		} catch (Exception e) {
			System.out.println("Unexpected exception. Process of the object");
		}
	}

	private boolean processSegment(String path) {
		// Get a segment, search the pattern and crea result in the node /comm/member-xx/segment
		String segmentPath = path + "/segment"; // el path era /comm/member-xx ahor ale añadimos el nodo segment.
		try {
			Stat s = zk.exists(segmentPath, false);
			byte[] data = zk.getData(segmentPath, false, s);
			ObjectMapper objectMapper = new ObjectMapper();
			Busqueda busqueda = objectMapper.readValue(data, Busqueda.class); // construir el objeto busqueda.
			
			int segmentIndex = busqueda.getIndice();
			LOGGER.info("[+] Segment: " + segmentIndex); // devolvemos por pantalla el indice del segmento.
			
			FASTABuscar buscar = new FASTABuscar(busqueda); // se crea un objeto FASTABuscar
			ArrayList<Long> result = buscar.buscar(busqueda.getPatron()); // se llama al metodo buscar (devuelve el resultado con el ïndice del segmento y las posiciones)
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(bos);
			oos.writeObject(result);
			oos.flush();
			byte[] bResult = bos.toByteArray();
			
			zk.delete(path, -1); // se elimina el nodo /comm/member-xx/segment
			
			String resultPath = path.substring(0, path.lastIndexOf("/")) + "/result"; // construimos el path a partir de /comm/member-xx añadiendo el nodo /result
			zk.create(resultPath, bResult, null, CreateMode.PERSISTENT);
			LOGGER.info("[+] Resultado: " + result);
			return true;
		} catch (KeeperException | InterruptedException e) {
			LOGGER.severe("[!] Error processing segment: " + e.getMessage());
			return false;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
	}

	private void printListMembers (List<String> list) {
		System.out.println("Remaining # members:" + list.size());
		for (Iterator<String> iterator = list.iterator(); iterator.hasNext();) {
			String string = (String) iterator.next();
			System.out.print(string + ", ");
		}
		System.out.println();
	}


	public static void main(String[] args) {
		FASTAProcess procesar = new FASTAProcess();
		try {
			//			Thread.sleep(60000);
			Thread.sleep(600000);
		} catch (Exception e) {
			LOGGER.severe("Error in main: " + e.getMessage());
		}

	}
}
