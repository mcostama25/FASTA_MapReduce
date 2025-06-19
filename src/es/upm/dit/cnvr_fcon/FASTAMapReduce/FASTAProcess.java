package es.upm.dit.cnvr_fcon.FASTAMapReduce;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

//import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
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

import es.upm.dit.cnvr_fcon.ZK.CreateSession;
import es.upm.dit.cnvr_fcon.FASTA_aux.Busqueda;
import es.upm.dit.cnvr_fcon.FASTA_aux.FASTABuscar;
import es.upm.dit.cnvr_fcon.FASTA_aux.Resultado;


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
	private String nodeSegment  = "/segment";
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
		CreateSession cs = new CreateSession();
		zk = cs.ConnectSession(hosts);
		
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
				LOGGER.info("[+] Se ha creado el nodo: " + CommMemberPath);
				
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
			String nodeMemberSegment = CommMemberPath + nodeSegment; // /comm/member-xx/segment
			try {
				LOGGER.info("[+] Se han actualizado los hijos de:" + CommMemberPath);
				Stat stat = zk.exists(nodeMemberSegment, watcherCommMember); // comprobamos si existe el nodo /comm/member-xx/segment
				if (stat != null) {
					processSegment(CommMemberPath);
				}
			} catch (KeeperException e) {
				LOGGER.severe("[!] Error processing node creation watcher: " + e.getMessage());
			} catch (InterruptedException e) {
				LOGGER.severe("InterruptedException en el watcher: " + e.getMessage());
			} catch (Exception e) {
				LOGGER.severe("Error inesperado en el watcher: " + e.getMessage());
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
		String resultPath = path + "/result"; // construimos el path a partir de /comm/member-xx añadiendo el nodo /result
		
		try {
			byte[] bytes = zk.getData(segmentPath, false, null);
			zk.delete(segmentPath, -1); // se elimina el nodo /comm/member-xx/segment
			// desreializamos el objeto busqueda del nodo /comm/member-xx/segment para su procesado
			ByteArrayInputStream in = new ByteArrayInputStream(bytes);
			ObjectInputStream is = new ObjectInputStream(in);
			Busqueda busqueda = (Busqueda) is.readObject();
			is.close();
			
			// una vez reconstuido el objeto busqueda, usamos sus metodos para su procesado:
			//recuperamos el indice
			int segmentIndex = busqueda.getIndice();

			FASTABuscar buscar = new FASTABuscar(busqueda); // se crea un objeto FASTABuscar
			ArrayList<Long> rawresult = buscar.buscar(busqueda.getPatron()); // se llama al metodo buscar (devuelve el resultado con el ïndice del segmento y las posiciones)
			// construimos la clase Resultado qeu contiene la lista de posicione sy el indice del subGenoma
			Resultado result = new Resultado(rawresult, segmentIndex);
			//ahora serializamos el resultado para colgarlo en el nodo /comm/member-xx/result
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(bos);
			oos.writeObject(result);
			oos.close();
			byte[] bResult = bos.toByteArray();
			
			//finalmente colgamos el objeto Result serializado al nodo /comm/member/result
			zk.create(resultPath, bResult, Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT); // creamos el nodo path+/reuslt con los datos (byte[]) del resultado
			LOGGER.info("[+] Resultado obtenido y colgado: " + resultPath + ": " + result);
			return true;
			
		} catch (KeeperException | InterruptedException e) {
			LOGGER.severe("[!] Error processing segment: " + e.getMessage());
			return false;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
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
			Thread.sleep(600000);
		} catch (Exception e) {
			LOGGER.severe("[!!] in main: " + e.getMessage());
		}

	}
}
