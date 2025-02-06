package es.upm.dit.cnvr_fcon.FASTAMapReduce;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

import es.upm.dit.cnvr_fcon.FASTA_aux.Busqueda;
import es.upm.dit.cnvr_fcon.FASTA_aux.FASTABuscar;
import es.upm.dit.cnvr_fcon.FASTA_aux.Resultado;

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
	//private String nodeASegment = "/segment-";
	private String nodeResult   = "/results";
	//private String nodeAResult  = "/result-";
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

		//TODO: Create the zkNodes required and set watchers
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
	private Watcher  watcherCommMember = new Watcher() {
		public void process(WatchedEvent event) {
			// TODO: process for getting and handling segments 
		}
	};


	// Notified when the number of children in /member is updated
	private Watcher  watcherAMember = new Watcher() {
		public void process(WatchedEvent event) {
			
			// Can be maintain, although is not needed in this implementation 
		}
	};

	private void getSegment(){

		// TODO: How to get a segment from /comm/member-xx/segment
		
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
			//			Thread.sleep(60000);
			Thread.sleep(600000);
		} catch (Exception e) {

		}

	}
}
