package es.upm.dit.cnvr_fcon.FASTAMapReduce;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.w3c.dom.events.Event;

import es.upm.dit.cnvr_fcon.FASTA_aux.BytesLeidos;
import es.upm.dit.cnvr_fcon.FASTA_aux.FASTALeerFichero;
import es.upm.dit.cnvr_fcon.FASTA_interface.ResultadoInterface;

/**
 * @author mmiguel, aalonso
 * @since   2023-11-20 
 */
public class FASTAMapReduce implements Watcher{

	/** 
	 * El objeto BytesLeidos obtenidos, con el genoma 
	 * y la cantidad de valores válidos
	 */ 
	private BytesLeidos rb; // genoma almacenado en un array

	/**
	 * Número de fragmentos en el genoma (contenido). Será el 
	 * número de hebras que habrá que invocar para procesarlos,
	 * al usar el monitor 
	 */
	private int numFragmentos  = 100;
	private int processedSegments = 0;
	private byte[] patron = null;
	private String fichero = null;
	private static String nodeMember = "/members";
	private String nodeComm     = "/comm";
	private String nodeSegment  = "/segment";
	private String nodeResult  = "/result";
	private ZooKeeper zk = null;
	String[] hosts = {"127.0.0.1:2181", "127.0.0.1:2181", "127.0.0.1:2181"};
	private String CommMemberPath = null; // definimos una variable donde guardaremos el path en el que se encuentra el nodo "/comm/member-xx"
	private String memberID = null; // guardamos el ID del member "member-xx"

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
		this.patron = patron;
		this.fichero = ficheroCromosoma;
		create_ZK_Nodes();
		configurarLogger();
		updateMembers(); // aqui se activa el watcher del nodo /members y se llama a la funcion assignSegment cuando se crea un nuevo miembro
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
			}  catch (Exception e) {
				LOGGER.severe("[!] Error creating session: " + e.getMessage());
			}
		} catch (Exception e) {
			LOGGER.severe("[!] Error creating session: " + e.getMessage());
		}
		
		//TODO: Create the zkNodes required and set watchers
		//Create node /members
		if (zk != null) {
			try {
				memberID = zk.create(nodeMember,null , null, CreateMode.PERSISTENT); // create /members
				zk.create(nodeComm, null, null, CreateMode.PERSISTENT); // create /comm
				LOGGER.info("[+] Created zNodes: " + nodeMember + " and " + nodeComm);

				memberID = memberID.substring(MemberID.lastIndexOf("/") + 1); // guardamos el ID del member "member-xx"
				CommMemberPath = nodeComm + "/" + memberID; // guardamos el path del nodo "/comm/member-xx"
				
			}catch (KeeperException | InterruptedException e) {
				LOGGER.severe("[!] Error creating zNodes: " + e.getMessage());
			}
		}
	}

	/*
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
			System.out.println("------------------Watcher ComMember------------------\n");
			try {
				// TODO: process for getting and handling segments 
				if (event.getType() == Event.EventType.NodeCreated) {
					LOGGER.info("[+] Nuevo nodo en:" + CommMemberPath);
					try {
						List<String> child = zk.getChildren(CommMemberPath, false);
						if ( child.get(0) == "result") {
							getResult(CommMemberPath + nodeResult, memberID); // cuando el hijo creado es un /result, hay que procesar el resultado.
						}
					}catch (KeeperException | InterruptedException e) {
						LOGGER.severe("[!] Error geting /comm/member-x children: " + e.getMessage());
					}
				} else if (event.getType() == Event.EventType.NodeDeleted) {
					LOGGER.info("[+] Nodo eliminado: " + CommMemberPath);
				}
				zk.getChildren(CommMemberPath, watcherCommMember); // volvemos a activar el Watcher.
			
			} catch (Exception e) {
				LOGGER.severe("[!] Error processing node creation watcher: " + e.getMessage());
			}
		}
	};

	// Obtiene un resultado de /comm/memberx y procesa.
	/*
	 * @param pathResult El path del resultado : /comm/member-xx/result
	 * @param member El ID del miembro : member-xx
	 */	
	private boolean getResult(String pathResult, String member){ // 
			//TODO: This implementation
			// Get and process a result

			try {
				byte[] data = zk.getData(pathResult, false, null); // obtenemos los datos del resultado
				ResultadoInterface resultado = new ResultadoInterface(data); // creamos un objeto ResultadoInterface con los datos obtenidos
				ArrayList<Long> res = processResult(resultado); // procesamos el resultado
				processedSegments++; // incrementamos el número de segmentos procesados

				LOGGER.info("[+] Resultado obtenido de: " + pathResult + ": " + res.size()); // Printamos el resultado obtenido.
				LOGGER.info("[+] Resultados obtenidos: " + res);

			} catch (KeeperException | InterruptedException e) {
				LOGGER.severe("[!] Error getting result: " + e.getMessage());
			}
			// Print the positions find, when all results are provided
			// Se han obtenido todos los resultados y hay que mostraro

			// Supose that res is a list after reduced when all results are provided
			// and stored in res
			/*
			LOGGER.info("Subgenomas donde se ha encontrado el patrón: " + this.patron);
			for (Long pos : res){
				System.out.println("Encontrado en la posición: " + pos);
			}
			*/
		return false;
	}

	/**
	 * Procesa el resultado de búsquda. Los índices buscados en un
	 * subgenoma se refieren a un subgenoma. Hay que actualizar su
	 * posición en el genoma global 1
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
			System.out.println("------------------Watcher Member------------------\n");
			try {
				// TODO: process for getting and handling segments
				if (event.getType() == Event.EventType.NodeCreated) {
					LOGGER.info("Nuevo nodo en:" + CommMemberPath);
					try {
						List<String> child = zk.getChildren(nodeMember, false);
						// TODO: cuando se crea un nuevo miembro hay que asignarle un fragmento.
						String node = nodeComm + "/" + child.get(0); // creamos la variable node = /comm/member-xx/
						assignSegment(node); // llamamos a la función assignSegment con el nodo creado.
					}catch (KeeperException | InterruptedException e) {
						LOGGER.severe("[!] Error geting /members children: " + e.getMessage());
					}	
				} else if (event.getType() == Event.EventType.NodeDeleted) {
					LOGGER.info("Nodo eliminado: " + CommMemberPath);
					// TODO: cuando se eleimina un miembro (ha fallado) hay que eliminarlo del nodo /comm y recupera rel reusltado si lo hay
				}
				zk.getChildren(nodeMember, watcherMembers); // volvemos a activar el Watcher.
			
			} catch (Exception e) {
				LOGGER.severe("[!] Error processing node creation watcher: " + e.getMessage());
			}
		}
	};

	// Procesa los miembros de /member y de /comm/memberxx, for checking  
	// whether it is needed to send a segment for a process or 
	// processing if a process has failed.
	private void updateMembers () { //Activamos watchers
		//TODO: to  be created
		try {
			List<String> members;
			members = zk.getChildren(nodeMember, watcherMembers); // activamos el watcher de /members
			printListMembers(members);

			List<String> CommMembers = zk.getChildren(CommMemberPath, watcherCommMember); // activamos el watcher de /comm/member-xx
		} catch (KeeperException | InterruptedException e) {
			LOGGER.severe("[!] Error updating members: " + e.getMessage());
		}
	}

	// Generate a segment and assigned it to a process
	private void assignSegment(String member) {
		//TODO: create a segment and assing it to a process
		try {
			int index = (int) (Math.random() * numFragmentos);
			byte[] subGenoma = getGenome(index, patron); // esta funcion es la que genera el subgenoma
			String pathSegment = member + "/segment"; // el nuevo path es /comm/member-xx/segment
			zk.create(pathSegment, subGenoma, null, CreateMode.PERSISTENT); // con esto se le asigna el segmento del genoma creado al miembro correposndiente.
			LOGGER.info("[+] Segmento asignado a: " + member);
		} catch (KeeperException | InterruptedException e) {
			LOGGER.severe("[!] Error assigning segment: " + e.getMessage());
		}
	}

	/**
	 * Genera un subArray que representa un subGenoma del genoma completo
	 * @param indice Índice del subgenoma
	 * @param patron El patron a buscar
	 * @return El subgenoma
	 */
	private byte[] getGenome(int indice, byte[] patron) {
		int tamanoSubGenoma = (rb.getGenoma().length / numFragmentos); // el tamaño de cada fragmento es igual al tamaño total de l Genoma / numFragmentos
		int inicioSubGenoma = tamanoSubGenoma * indice ; // indica la posición de inicio del subgenoma (para un indice X, el inicio es X * tamañoSubGenoma)
		int longitudSubGenoma;

		if ((indice + 1) != numFragmentos) {
			longitudSubGenoma = tamanoSubGenoma - 1 + patron.length; // para cualquier fragmento que no sea el último:
		} else {
			longitudSubGenoma = tamanoSubGenoma - 1; // para el último fragmento: 
		}

		byte[] subGenoma = Arrays.copyOfRange(rb.getGenoma(), inicioSubGenoma, inicioSubGenoma + longitudSubGenoma);
		LOGGER.fine("obtenerSubGenoma: " + inicioSubGenoma + " " + longitudSubGenoma);
		return subGenoma;
	}

	public static void main(String[] args) {
		String fichero = "ref100K.fa";
		String patronS  = "TGAAGCTA";;
		byte[] patron = patronS.getBytes();
		Int numFragmentos = 100;
		FASTAMapReduce generar = new FASTAMapReduce(fichero, patron);
		//generar.FASTAMap(patron);

		//FASTAProcess procesar = new FASTAProcess();
		try {
			Thread.sleep(600000);
		} catch (Exception e) {
			LOGGER.severe("Main thread interrupted: " + e.getMessage());
		}

	}

}
