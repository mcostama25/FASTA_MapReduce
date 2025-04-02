package es.upm.dit.cnvr_fcon.FASTAMapReduce;

import java.io.ByteArrayOutputStream;
//import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.data.Stat;
import org.w3c.dom.events.Event;

import com.fasterxml.jackson.databind.ObjectMapper;

import es.upm.dit.cnvr_fcon.FASTA_aux.Busqueda;
import es.upm.dit.cnvr_fcon.FASTA_aux.FASTABuscar;

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
	private ZooKeeper zk = null;
	private static final int SESSION_TIMEOUT = 5000;
	private Lock lock = new ReentrantLock();
	String[] hosts = {"127.0.0.1:2181", "127.0.0.1:2181", "127.0.0.1:2181"};
	
	private String CommMemberPath = null; // definimos una variable donde guardaremos el path en el que se encuentra el nodo "/comm/member-xx"

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
		Random rand = new Random();
		int i = rand.nextInt(hosts.length);
		
		try {
			if (zk == null) {
				zk = new ZooKeeper(hosts[i], SESSION_TIMEOUT, this); // creamos una session de zookeeper conectando a uno de los hosts.
				try {
					lock.lock();
					System.out.println("Cerrado el cerrojo para crear la sesión");
				} catch (Exception e) {
					LOGGER.severe("[!] Error al cerrar el cerrojo: " + e.getMessage());
				} 
			}
		} catch (Exception e) {
			LOGGER.severe("[!] Error creating session: " + e.getMessage());
		}
		
		//TODO: Create the zkNodes required and set watchers
		//Create node /members
		if (zk != null) {
			try { 
				if (zk.exists(nodeMember, false) == null) {
                zk.create(nodeMember, new byte[0], Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
	            }
	            if (zk.exists(nodeComm, false) == null) {
	                zk.create(nodeComm, new byte[0], Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
	            }
	            LOGGER.info("[+] Created zNodes: " + nodeMember + " and " + nodeComm);
	            
				String MemberID = zk.create(nodeMember + nodeAMember, new byte[0], Ids.OPEN_ACL_UNSAFE,  CreateMode.EPHEMERAL_SEQUENTIAL); // guardamos el path del member creado "/members/member-xx"
				MemberID = MemberID.substring(MemberID.lastIndexOf("/") + 1); // guardamos el ID del member "member-xx"

				CommMemberPath = nodeComm + "/" + MemberID; // guardamos el path del nodo "/comm/member-xx"
				zk.create(CommMemberPath, new byte[0], Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT); // creamos el nodo "/comm/member-xx" usando el MemberID generado anteriormente.
				LOGGER.info("[+] Created zNodes: " + nodeMember + " " + nodeComm);
				
			}catch (KeeperException | InterruptedException e) {
				LOGGER.severe("[!] Error creating zNodes: " + e.getMessage());
			}
		}
	}

	/**
	 * Configuracion de un logger
	 */
	private void configurarLogger() {
		//Configurar un handler
		ConsoleHandler handler = new ConsoleHandler(); 
		handler.setLevel(Level.FINEST); 
		LOGGER.addHandler(handler); 
		LOGGER.setLevel(Level.FINEST);
	}

	// Notified when the number of children in /comm/memberxx is updated
	private Watcher  watcherCommMember = new Watcher() { // este wacher se levanta al crearse el nodo /comm y va estar monitorizando la cracion y destruccion de hijos.
		public void process(WatchedEvent event) {
			System.out.println("------------------Watcher ComMember------------------\n");
			try {
				// TODO: process for getting and handling segments 
				if (event.getType() == Event.EventType.NodeCreated) {
					LOGGER.info("Nuevo nodo en:" + CommMemberPath);
					try {
						List<String> child = zk.getChildren(CommMemberPath, false);
						if ( child.get(0).equals("segment")) {
							processSegment(CommMemberPath); // cuando el hijo creado es un /segment, se llama a la funcion processSegment(/comm/member-xx);
						}
					}catch (KeeperException | InterruptedException e) {
						LOGGER.severe("[!] Error geting /comm/member-x children: " + e.getMessage());
					}	
				} else if (event.getType() == Event.EventType.NodeDeleted) {
					LOGGER.info("Nodo eliminado: " + CommMemberPath);
				}
				zk.getChildren(CommMemberPath, watcherCommMember); // volvemos a activar el Watcher.
			
			} catch (Exception e) {
				LOGGER.severe("[!] Error processing node creation watcher: " + e.getMessage());
			}
		}
	};

	private void getSegment(){
		// TODO: How to get a segment from /comm/member-xx/segment	ACTIVAR EL WACTHER
		try {
			zk.getChildren(CommMemberPath, watcherCommMember);
		} catch (KeeperException | InterruptedException e) {
			LOGGER.severe("[!] Error al activar el watcher:" + e.getMessage());
		} // se activa el watcher para el nodo "/comm/member-xx"
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
		// Get a segment, search the pattern and create a result in the node /comm/member-xx/segment
		String segmentPath = path + "/segment"; // el path era /comm/member-xx ahora le añadimos el nodo /segment.
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
			zk.create(resultPath, bResult, null, CreateMode.PERSISTENT); // creamos el nodo path+/reuslt con los datos (byte[]) del resultado
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
			// Thread.sleep(60000);
			Thread.sleep(600000);
		} catch (Exception e) {
			LOGGER.severe("[!!] in main: " + e.getMessage());
		}

	}
}
