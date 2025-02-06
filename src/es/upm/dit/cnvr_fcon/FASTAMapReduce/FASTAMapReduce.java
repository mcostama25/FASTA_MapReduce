package es.upm.dit.cnvr_fcon.FASTAMapReduce;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.logging.*;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

import es.upm.dit.cnvr_fcon.FASTA_aux.Busqueda;
import es.upm.dit.cnvr_fcon.FASTA_aux.BytesLeidos;
import es.upm.dit.cnvr_fcon.FASTA_aux.FASTALeerFichero;
import es.upm.dit.cnvr_fcon.FASTA_aux.Resultado;
import es.upm.dit.cnvr_fcon.FASTA_interface.BusquedaInterface;
import es.upm.dit.cnvr_fcon.FASTA_interface.ResultadoInterface;

import es.upm.dit.cnvr_fcon.ZK.CreateSession;
import es.upm.dit.cnvr_fcon.ZK.CreateZNode;

/**
 * @author mmiguel, aalonso
 * @since   2023-11-20 
 */
public class FASTAMapReduce implements Watcher{

	/** 
	 * El objeto BytesLeidos obtenidos, con el genoma 
	 * y la cantidad de valores válidos
	 */ 
	private BytesLeidos rb;

	/**
	 * Número de fragmentos en el genoma (contenido). Será el 
	 * número de hebras que habrá que invocar para procesarlos,
	 * al usar el monitor 
	 */
	private int     numFragmentos  = 100;

	private byte[] patron = null;
	private static String nodeMember = "/members";
	private String nodeComm     = "/comm";
	private String nodeSegment  = "/segment";
	//private String nodeResult  = "/results";
	private ZooKeeper zk = null;

	//Create required objetos for handling processes, segments and results
	
	static {
		System.setProperty("java.util.logging.SimpleFormatter.format",
				"[%1$tF %1$tT][%4$-7s] [%5$s] [%2$-7s] %n");
	}

	static final Logger LOGGER = Logger.getLogger(FASTAMapReduce.class.getName());

	/**
	 * Constructor que crea un cromosoma a partir de un fichero
	 * @param ficheroCromosoma Nombre del fichero con el genoma
	 * @param patron El patrñon que se debe buscar
	 */
	public FASTAMapReduce(String ficheroCromosoma, byte[] patron) {
		try {
			FASTALeerFichero leer = new FASTALeerFichero(ficheroCromosoma);
			this.rb = leer.getBytesLeidos(); 
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
		//TODO: Create the required variables
		create_ZK_Nodes();
		configurarLogger();
		updateMembers();

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

	// Mostrar los hijos de un zNode, que se recibe en list
	private void printListMembers (List<String> list) {
		String out = "Remaining # members:" + list.size();
		for (Iterator<String> iterator = list.iterator(); iterator.hasNext();) {
			String string = (String) iterator.next();
			out = out + string + ", ";				
		}
		out = out + "\n";
		LOGGER.finest(out);
	}

	// Create and configure the zkNodes and connection to the ensemble 
	// Crear y configurar los zkNodes y crear una sesión con un ensemble 
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
		handler.setLevel(Level.FINE); 
		LOGGER.addHandler(handler); 
		LOGGER.setLevel(Level.FINE);
	}

	// Watcher: se recibe una notificación cuando ha cambiado el número de hijos de /comm/memberxx.
	// Se debe crear esperar a cambios en todos los hijos de /comm/. No hay que esperar a cambio
	// de /comm

	// Se recibe un watcher cuando se recibe un segmento. Cuando se borra un hijo o hay un segmento,
	// no hay que hacer nada.
	private Watcher  watcherCommMember = new Watcher() {
		public void process(WatchedEvent event) {

			// TODO: process for getting and handling results 
		}
	};

	// Obtiene un resultado de /comm/memberx y procesa.
	private boolean getResult(String pathResult, String member){
			//TODO: This implementation

			// Get and process a result
			
			// Print the positions find, when all results are provided
			// Se han obtenido todos los resultados y hay que mostraro

			// Supose that res is a list after reduced when all results are provided
			// and stored in res
			
/*				LOGGER.info("Subgenomas donde se ha encontrado el patrón: " + this.patron);
				for (Long pos : res){
					System.out.println("Encontrado " + / " en " + pos);
				}
*/

		return false;
	}

	/**
	 * Procesa el resultado de búsquda. Los índices buscados en un
	 * subgenoma se refieren a un subgenoma. Hay que actualizar su
	 * posición en el genoma global 
	 * @param resultado El resultado de una búsqueda
	 * @return La lista actualizada
	 */
	private ArrayList <Long> processResult(ResultadoInterface resultado) {

		int indice;
		ArrayList<Long> lista = null;

		// TODO: Process a result

		// Devuelve null si no se han recibido todos los resultados
		return lista;
	}
	
	// Se recibe una notificación cuando cambia el número de hijos de /member
	private Watcher  watcherMembers = new Watcher() {
		public void process(WatchedEvent event) {
			//TODO: get changes in the children of /member
		}
	};

	// Procesa los miembros de /member y de /comm/memberxx, for checking  
	// whether it is needed to send a segment for a process or 
	// processing if a process has failed.
	private void updateMembers () {
		//TODO: to  be created
	}

	// Generate a segment and assigned it to a process
	private void assignSegment(String member) {

		//TODO: create a segment and assing it to a process
	}




	/**
	 * Genera un subArray que representa un subGenoma del genoma completo
	 * @param numSubGenoma Índice del subgenoma
	 * @param patron El patron a buscar
	 * @return El subgenoma
	 */
	private byte[] getGenome(int indice, byte[] patron) {

		int tamanoSubGenoma = (rb.getGenoma().length / numFragmentos); 
		int inicioSubGenoma = tamanoSubGenoma * indice ;
		int longitudSubGenoma;

		if ((indice + 1) != numFragmentos) {
			longitudSubGenoma = tamanoSubGenoma - 1 + patron.length;
		} else {
			longitudSubGenoma = tamanoSubGenoma - 1;
		}

		//old 
		//byte[] subGenoma = new byte[longitudSubGenoma];
		//System.arraycopy(this.rb.getGenoma(), inicioSubGenoma, subGenoma, 0, longitudSubGenoma);    	

		byte[] subGenoma = Arrays.copyOfRange(rb.getGenoma(), inicioSubGenoma, inicioSubGenoma + longitudSubGenoma);

		LOGGER.fine("obtenerSubGenoma: " + inicioSubGenoma + " " + longitudSubGenoma);

		return subGenoma;
	}


	public static void main(String[] args) {
		//long t0 =System.currentTimeMillis();


		//String fichero = "ref100.fa";
		String fichero = "ref100K.fa";
		//String fichero = "chr19.fa";

		String patronS  = "TGAAGCTA";;
		byte[] patron = patronS.getBytes();

		FASTAMapReduce generar = new FASTAMapReduce(fichero, patron);
		//generar.FASTAMap(patron);

		//FASTAProcess procesar = new FASTAProcess();
		try {
			Thread.sleep(600000);
		} catch (Exception e) {

		}

	}

}
