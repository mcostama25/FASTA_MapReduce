package es.upm.dit.cnvr_fcon.FASTAMapReduce;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.zookeeper.*;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.data.Stat;

import es.upm.dit.cnvr_fcon.ZK.CreateSession;
import es.upm.dit.cnvr_fcon.FASTA_aux.Busqueda;
import es.upm.dit.cnvr_fcon.FASTA_aux.BytesLeidos;
import es.upm.dit.cnvr_fcon.FASTA_aux.FASTALeerFichero;
import es.upm.dit.cnvr_fcon.FASTA_aux.Resultado;
/**
 * @author mmiguel, aalonso / Martí Costa
 * @since 2023-11-20
 */
public class FASTAMapReduce implements Watcher {

	/**
	 * El objeto BytesLeidos obtenidos, con el genoma y la cantidad de valores
	 * válidos
	 */
	private BytesLeidos rb; // genoma almacenado en un array

	/**
	 * Número de fragmentos en el genoma (contenido). Será el número de hebras que
	 * habrá que invocar para procesarlos, al usar el monitor
	 */
	
	private List<String> members;
	
	private int numFragmentos = 100;
	private int num_segmentosAsignados = 0;
	private ArrayList<Integer> segmentos_asignados = new ArrayList<>();
	private ArrayList<Integer> segmentos_acabados = new ArrayList<>();
	
	private byte[] patron = null;
	private String fichero = null;
	
	private static String nodeMember = "/members";
	private String nodeComm = "/comm";
	private String nodeSegment = "/segment";
	private String nodeResult = "/result";
	
	private ZooKeeper zk = null;
	
	private  ArrayList<Long> Resultat_Final = new ArrayList<>(); // variable para guardar el resultado final, lista con todas las posciones
	
	String[] hosts = { "127.0.0.1:2181", "127.0.0.1:2181", "127.0.0.1:2181" };
	private String CommMemberPath = null; // definimos una variable donde guardaremos el path en el que se encuentra el
											// nodo "/comm/member-xx"

	static {
		System.setProperty("java.util.logging.SimpleFormatter.format", "[%1$tF %1$tT][%4$-7s] [%5$s] [%2$-7s] %n");
	}

	static final Logger LOGGER = Logger.getLogger(FASTAMapReduce.class.getName());

	/**
	 * Constructor que crea un cromosoma a partir de un fichero
	 * 
	 * @param ficheroCromosoma Nombre del fichero con el genoma
	 * @param patron           El patrñon que se debe buscar
	 */
	public FASTAMapReduce(String ficheroCromosoma, byte[] patron) {
		try {
			FASTALeerFichero leer = new FASTALeerFichero(ficheroCromosoma);
			this.rb = leer.getBytesLeidos();
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
		// TODO: Create the required variables
		this.patron = patron;
		this.fichero = ficheroCromosoma;
		
		configurarLogger();
		create_ZK_Nodes();
		updateMembers(); // aqui se activa el watcher del nodo /members y se llama a la funcion
							// assignSegment cuando se crea un nuevo miembro
	}

	@Override
	public void process(WatchedEvent event) {
		try {
			System.out.println("Unexpected invocated this method. Process of the object");
			List<String> list = zk.getChildren(nodeSegment, null);// watcherSegment); //this);
			printListMembers(list);

		} catch (Exception e) {
			System.out.println("Unexpected exception. Process of the object");
		}
	}

	// Mostrar los hijos de un zNode, que se recibe en list
	private void printListMembers(List<String> list) {
		String out = "Remaining # members:" + list.size();
		for (Iterator<String> iterator = list.iterator(); iterator.hasNext();) {
			String string = (String) iterator.next();
			out = out + string + ", ";
		}
		out = out + "\n";
		LOGGER.finest(out);
	}

	//__________________________ GESTION DE NODOS ________________________________


	// create_ZK_Nodes
	// Create and configure the zkNodes and connection to the ensemble
	// Crear y configurar los zkNodes y crear una sesión con un ensemble
	private void create_ZK_Nodes(){
		//Create Session to zk
		CreateSession cs = new CreateSession();
		zk = cs.ConnectSession(hosts);
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
	        } catch (KeeperException | InterruptedException e) {
	            LOGGER.severe("[!] Error creating zNodes: " + e.getMessage());
	        }
	    }
	}

	// configurarLogger
	private void configurarLogger() {
		ConsoleHandler handler = new ConsoleHandler();
		handler.setLevel(Level.FINE);
		LOGGER.addHandler(handler);
		LOGGER.setLevel(Level.FINE);
	}

	// updateMembers
	// Procesa los miembros de /member y de /comm/memberxx, for checking
	// whether it is needed to send a segment for a process or
	// processing if a process has failed.
	private void updateMembers() { // Activamos watchers
		// TODO: to be created
		try {
			members = zk.getChildren(nodeMember, watcherMembers); // activamos el watcher de /members
			LOGGER.info("[+] Miembros registrados: "+ members);
			
			for (String member : members) {
				CommMemberPath = nodeComm + "/" + member; // creamos la variable commemberPath
				zk.getChildren(CommMemberPath, watcherCommMember); // activamos el watcher de /comm/member-xx para todos los miembros previamente creados
				assignSegment(CommMemberPath); // asignamos un segmento a los miembros previamentre existentes.
			}
		} catch (KeeperException | InterruptedException e) {
			LOGGER.severe("[!] Error updating members: " + e.getMessage());
		}
	}

	private void deleteall() { // función que elimine todos los nodos cuando se devualva el resultado final.
		List<String> members; 
		List<String> comm;
		
		try {
			members = zk.getChildren(nodeMember, false);
			comm = zk.getChildren(nodeMember, false);
			
			for (String child : members ) {
				zk.delete(nodeMember+"/"+child, -1);
			}
			for (String child : comm) {
				zk.delete(nodeComm+"/"+child, -1);
			}
			
			zk.delete(nodeComm, -1);
			zk.delete(nodeMember, -1);
			
		} catch (KeeperException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
	}

	//__________________________ PROCESAR GENOMA ________________________________

	
	// assignSegment
	// Generate a segment and assigned it to a process
	private void assignSegment(String member) { // String memmber == /comm/member-xx
		// SE ASIGNA UNA CLASE BUSQUED AL NODO /comm/member-xx/segment que contiene el subgenoma, el indice y el patorn.
		String pathSegment = member + "/segment"; // el nuevo path es /comm/member-xx/segment
		Busqueda busqueda;
		
		byte[] subgenoma = null;
		//Busqueda segment;
		
		LOGGER.info("[*] Segmento: "+num_segmentosAsignados+ " asignado");
		segmentos_asignados.add(num_segmentosAsignados);
		
		if (numFragmentos > num_segmentosAsignados) { // miramos si quedan segmentos para asignar
			subgenoma = getGenome(num_segmentosAsignados, patron);
			busqueda = new Busqueda(subgenoma, patron, num_segmentosAsignados);
		} else {
			LOGGER.info("No hay más segmentos disponibles para asignar.");
			return ;
		}
		
		try {
			Thread.sleep(25);
			// ahora se construye la classe BUSQUEDA:
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(bos);
			oos.writeObject(busqueda);
			oos.close();
			byte[] data = bos.toByteArray();
			
			// se crea el nodo /com/member-xx/segment con los datos de la clase busqyeda en formato ByteArray
			zk.create(pathSegment, data, Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT); // con esto se le asigna el segmento del
																		// genoma creado al miembro correposndiente.
			
			LOGGER.info("[+] Numero de segmentos asignados: " + segmentos_asignados.size());
			LOGGER.info("[*]" + segmentos_asignados);
			
			num_segmentosAsignados ++;
		} catch (KeeperException | InterruptedException e) {
			LOGGER.severe("[!] Error assigning segment: " + e.getMessage());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	// getGenome
	/*
	 * Genera un subArray que representa un subGenoma del genoma completo
	 * @param indice Índice del subgenoma
	 * @param patron El patron a buscar
	 * @return El subgenoma
	 */
	private byte[] getGenome(int indice, byte[] patron) {
		int tamanoSubGenoma = (rb.getGenoma().length / numFragmentos); // el tamaño de cada fragmento es igual al tamaño
																		// total de l Genoma / numFragmentos
		int inicioSubGenoma = tamanoSubGenoma * indice; // indica la posición de inicio del subgenoma (para un indice X,
														// el inicio es X * tamañoSubGenoma)
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


	//__________________________ RESULTADOS ________________________________


	// GETRESULT
	// Obtiene un resultado de /comm/memberx y procesa.
	/*
	 * @param pathResult El path del resultado : /comm/member-xx/result
	 * 
	 * @param member El ID del miembro : member-xx
	 */
	private boolean getResult(String pathResult, String member) { //
		try {
			// obtenemos el reusltado serializado dell nodo /comm/member-xx/result y lo deserializamos
			byte[] data = zk.getData(pathResult, false, null); // obtenemos los datos del resultado
			ByteArrayInputStream in = new ByteArrayInputStream(data);
			ObjectInputStream is = new ObjectInputStream(in);
			Resultado result = (Resultado)is.readObject();
			is.close();
			
			zk.delete(pathResult, -1);
						
			ArrayList<Long> Lista_Resultado_Final = processResult(result); // procesamos el resultado parcial para obtener el reusltado final.
			if (Lista_Resultado_Final != null) {
				LOGGER.info("[+][!][+] Se ha obtenido el reusltado final: \n");
				for (Long pos : Lista_Resultado_Final){
					System.out.println("Patron encontrado en la posición: " + pos);
				}
				
				deleteall();
				System.exit(0);
				
			} else {
				LOGGER.info("[+] Todavia quedan segmentos a procesar.");
			}
			return true;
			
		} catch (KeeperException | InterruptedException e) {
			LOGGER.severe("[!] Error getting result: " + e.getMessage());
		} catch (IOException e) {
			LOGGER.severe("[!] Error de I/O: " + e.getMessage());
		} catch (ClassNotFoundException e) {
			LOGGER.severe("[!] Error de ClassNotFound: " + e.getMessage());
		}
		return false;
	}

	// PROCESSRESULT
	/**
	 * Procesa el resultado de búsquda. Los índices buscados en un subgenoma se
	 * refieren a un subgenoma. Hay que actualizar su posición en el genoma global 1
	 * 
	 * @param resultado El resultado de una búsqueda
	 * @return La lista actualizada
	 */
	private ArrayList<Long> processResult(Resultado resultado) {
		// TODO: Process a result
		int tamanoSubGenoma = (rb.getGenoma().length / numFragmentos); // el tamaño de cada fragmento es igual al tamaño
		int indice = resultado.getIndice();
		ArrayList<Long> lista = resultado.getLista(); // posiciones del resultado parcial
		
		try {
			if (lista != null) { // si la lista del reusltado del segmento no esta vacia, la procesamos.
				for (long pos : lista) {
					long posicion = (indice * tamanoSubGenoma) + pos; // calculamos la posicion dentro del genoma total como: indice_subgenoma*Tamaño_subgenoma + posicion dentor de subgenoma 
					Resultat_Final.add(posicion); // añadimos a la lista final el reusltado parcial (las posiciones reales)
				}
			}
			
			segmentos_acabados.add(indice); // añadimos el indice del segmento a la lista de control
			LOGGER.info("[+] Número de segmentos procesados:" + segmentos_acabados.size());
			
			if (segmentos_acabados.size() == numFragmentos) {
				System.out.println("[+] RESULTADO FINAL!!!: " + Resultat_Final);
				return Resultat_Final; // cuando se hayan procesado todos los segmentos, devolvemos el resultado final
			}
			
		} catch (Exception e) {
			LOGGER.severe("Error al procesar los resultados: " + e.getMessage());
		}
		LOGGER.info("[+] Se ha actualizado la lista de resultados: " + Resultat_Final);
		return null; // Devuelve null si no se han recibido todos los resultados		
	}

	
	
	//__________________________ WATHCERS________________________________


	// WatcherMembers
	// Se recibe una notificación cuando cambia el número de hijos de /member
	private Watcher watcherMembers = new Watcher() {
		public void process(WatchedEvent event) {
			// TODO: get changes in the children of /member
			System.out.println("------------------Watcher Member------------------\n");
			try {
				// TODO: process for getting and handling segments
				Event.EventType eventType = event.getType();
				if (eventType == Event.EventType.NodeChildrenChanged) {
					try {
						updateMembers(); // llamamos a la funcion updateMember spara tener un regiustro de los miembros y activar el watcher CommMember para el nuevo.
						List<String> children = zk.getChildren(nodeMember, false);
						Collections.sort(children);
						if (!children.isEmpty()) {
							String newSon = nodeComm + "/" + children.get(children.size() - 1);
							assignSegment(newSon);
						}
					} catch (KeeperException | InterruptedException e) {
						LOGGER.severe("[!] Error obteniendo hijos de /members: "+e.getMessage());
						}
				} else if (eventType == Event.EventType.NodeDeleted) {
					LOGGER.info("Nodo eliminado: " + CommMemberPath);
					updateMembers(); // llamamos a la funcion updateMember spara tener un registro de los miembros.
					// TODO: cuando se eleimina un miembro (ha fallado) hay que eliminarlo del nodo
					// /comm y recupera rel reusltado si lo hay
					// POR HACER
				} else {
					LOGGER.info("____No se ha detectado al cración ni eliminación de Nodo MEMBERS______");
				}
				zk.getChildren(nodeMember, watcherMembers); // volvemos a activar el Watcher.

			} catch (Exception e) {
				LOGGER.severe("[!] Error processing node creation watcher 1: " + e.getMessage());
			}
		}
	};

	// WatcherCommMember:
	// Watcher: se recibe una notificación cuando ha cambiado el número de hijos de /comm/memberxx.
	// Se debe crear esperar a cambios en todos los hijos de /comm/. No hay que
	// esperar a cambio
	// de /comm

	// Se recibe un watcher cuando se recibe un segmento. Cuando se borra un hijo o
	// hay un segmento,
	// no hay que hacer nada.
	private Watcher watcherCommMember = new Watcher() {
		public void process(WatchedEvent event) {

			System.out.println("------------------Watcher ComMember------------------\n");
			String CommMemberPath = event.getPath(); // obtenemos el path del evento
			String memberID = CommMemberPath.substring(CommMemberPath.lastIndexOf("/") + 1); //guardamos el nobre del miemrbo en cuestion
			String pathResult = CommMemberPath + nodeResult; // creamos el path del resultado
			LOGGER.info("[+] Nuevo nodo en:" + memberID);
			try {
				Thread.sleep(25);
				Stat stat = zk.exists(pathResult, false); // comprobamos si existe en nodo /comm/memeber-xx/result
				if(stat != null) {
					getResult(pathResult, memberID);
					
					if (num_segmentosAsignados < numFragmentos) {
						assignSegment(CommMemberPath);
					}
				}
				zk.getChildren(CommMemberPath, watcherCommMember); // volvemos a activar el Watcher.

			} catch (Exception e) {
				LOGGER.severe("[!] Error processing node creation watcher 2: " + e.getMessage());
			}
		}
	};

	//__________________________ MAIN ________________________________


	public static void main(String[] args) {
		Logger.getLogger("org.apache.zookeeper").setLevel(Level.INFO);
		String fichero;
		String patronS;
		if (args.length == 2) {
			fichero = args[0];
			patronS = args[1];
		} else {
			fichero = "cromosomas/ref100K.fa";
			patronS = "TGAAGCTA";
			System.out.println("[+] Utilizando fichero " + fichero + "y patron " + patronS);
		}
		
		byte[] patron = patronS.getBytes();
		int numFragmentos = 100;
		FASTAMapReduce generar = new FASTAMapReduce(fichero, patron);
		// generar.FASTAMap(patron);

		// FASTAProcess procesar = new FASTAProcess();
		try {
			Thread.sleep(600000);
		} catch (Exception e) {
			LOGGER.severe("Main thread interrupted: " + e.getMessage());
		}

	}

}
