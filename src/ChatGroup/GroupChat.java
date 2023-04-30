package ChatGroup;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

public class GroupChat {
	private static final String TERMINATE = "/Leave";
	private static final String COMMAND = "/Commands";
	static String name;
	static volatile boolean finished = false;
	static volatile boolean loggedIn = false;
	static volatile boolean CHTBusy = false;
	private static final List<String> chatHistory = new ArrayList<>();
	private static final List<Integer> chatRooms = new ArrayList<>();

	@SuppressWarnings("deprecation")
	public static void main(String[] args) {
		try (Scanner sc = new Scanner(System.in)) {
			boolean loggedIn = false;
			while (!loggedIn) {
				Scanner scnr = new Scanner(System.in);
				System.out.println("Enter '1' to login or '2' to register");
				String option = scnr.nextLine();
				if (option.equals("1")) {
					String username = login(scnr);
					if (username == null) {
						System.out.println("Login failed.");
						return;
					}
					name = username;
					loggedIn = true;
				} else if (option.equals("2")) {
					register(scnr);
				} else {
					System.out.println("Invalid option.");
				}
			}

			boolean CHTBusy = false;
			while (!CHTBusy) {
				Scanner scnr = new Scanner(System.in);
				System.out.println(
						"Enter '1' to Create a Chat Room, '2' to Join a Chat Room, '3' to update account info:, or '4' to Log Out");
				String option = scnr.nextLine();
				if (option.equals("1")) {
					String[] chatRoomInfo = createChatRoom(scnr);
					String multicastIP = chatRoomInfo[0];
					int port = Integer.parseInt(chatRoomInfo[1]);
					System.out.println(
							"Chat room created with multicast IP: " + multicastIP + " and port number: " + port);
				}

				else if (option.equals("2")) {
					String[] chatRoomInfo = joinChatRoom(name, scnr); // Pass the user's username

					if (chatRoomInfo != null) {
						String multicastIP = chatRoomInfo[0];
						int port = Integer.parseInt(chatRoomInfo[1]);
						System.out.println(
								"Joined chat room with multicast IP: " + multicastIP + " and port number: " + port);
						startChat(multicastIP, port); // Call the startChat() method
					}

				} else if (option.equals("3")) {
					updateAccountInfo(scnr);
				} else if (option.equals("4")) {
					loggedIn = false;
					main(args);
				} else {
					System.out.println("Invalid option.");
				}
			}

			try (Scanner chtscnr = new Scanner(System.in)) {
				System.out.print("Enter the multicast host: ");
				String multicastHost = chtscnr.nextLine();
				System.out.print("Enter the port number: ");
				int port = Integer.parseInt(chtscnr.nextLine());
				try {
					InetAddress group = InetAddress.getByName(multicastHost);
					System.out.println(name + " has joined the Chat Room!");
					MulticastSocket socket = new MulticastSocket(port);
					// Since we are deploying
					socket.setTimeToLive(1);
					// this on localhost only (For a subnet set it as 1)
					socket.joinGroup(group);
					Thread t = new Thread(new ReadThread(socket, group, port));
					// Spawn a thread for reading messages
					t.start();
					// Announcing member joining the chat group
					String introMsg = name + " joined the room*";
					byte[] bufferIntro = introMsg.getBytes();
					DatagramPacket datagramIntro = new DatagramPacket(bufferIntro, bufferIntro.length, group, port);
					socket.send(datagramIntro);
					// sent to the current group
					try {
						Scanner chatHistoryScanner = new Scanner(new File("chat_history.txt"));
						System.out.println("Chat history:");
						while (chatHistoryScanner.hasNextLine()) {
							String message = chatHistoryScanner.nextLine();
							chatHistory.add(new String(message));
							System.out.println(message);
						}
						System.out.println();
					} catch (FileNotFoundException e) {
						System.err.println("Chat history file not found");
						e.printStackTrace();
					}
					System.out.println("Start typing messages...\n");
					while (true) {
						String message = chtscnr.nextLine();
						if (message.equalsIgnoreCase(GroupChat.TERMINATE)) {
							// Announcing member leaving the chat group
							String outroMsg = name + " left the room*";
							byte[] bufferOutro = outroMsg.getBytes();
							DatagramPacket datagramOutro = new DatagramPacket(bufferOutro, bufferOutro.length, group,
									port);
							socket.send(datagramOutro);
							System.out.println(name + " has left the Chat Room");
							finished = true;
							socket.leaveGroup(group);
							socket.close();
							break;
						} else if (message.equalsIgnoreCase(GroupChat.COMMAND)) {
							System.out.println("System Commands:");
							System.out.println("To leave: /Leave\n");
						} else {
							// Send the message to the group chat
							String chatMsg = name + ": " + message;
							byte[] buffer = chatMsg.getBytes();
							DatagramPacket datagram = new DatagramPacket(buffer, buffer.length, group, port);
							socket.send(datagram);
						}
					}
				} catch (SocketException se) {
					System.out.println("Error creating Chatbox");
					se.printStackTrace();
				} catch (IOException ie) {
					System.out.println("Error reading/writing from/to Chatbox");
					ie.printStackTrace();
				}
			}
		} catch (Exception e) {
			System.err.println("An error occurred: " + e.getMessage());
			loggedIn = false;
			main(args);
		}
	}

	private static String[] createChatRoom(Scanner scnr) {
		System.out.print("Enter the chat room name: ");
		String chatRoomName = scnr.nextLine();

		try {
			String[] existingChatRoom = getChatRoomByName(chatRoomName);

			if (existingChatRoom != null) {
				// JOIN THE CHAT ROOM
				System.out.println("HEY YOU NEED TO JOIN THE CHAT ROOM");

				return Arrays.copyOfRange(existingChatRoom, 1, existingChatRoom.length);

			} else {
				// CREATE THE CHAT ROOM

				// Generate a random multicast IP address and port number
				Random random = new Random();
				String multicastIP = "239." + (random.nextInt(255) + 1) + "." + (random.nextInt(255) + 1) + "."
						+ (random.nextInt(255) + 1);
				int port = random.nextInt(16383) + 1024; // Generate a random port number between 1024 and 49151

				// Save the chat room information to a text file
				try (FileWriter fw = new FileWriter("chatrooms.txt", true);
						BufferedWriter bw = new BufferedWriter(fw);
						PrintWriter out = new PrintWriter(bw)) {
					out.println(chatRoomName + "," + multicastIP + "," + port);
				} catch (IOException e) {
					System.err.println("Error saving chat room to file");
					e.printStackTrace();
				}

				return new String[] { multicastIP, Integer.toString(port) };
			}

		} catch (Exception e) {
			System.out.println(e);
		}

		return null;
	}

	private static String[] getChatRoomByName(String name) throws IOException {
		try (Scanner fileScanner = new Scanner(new File("chatrooms.txt"))) {
			while (fileScanner.hasNextLine()) {
				String[] line = fileScanner.nextLine().split(",");
				if (line[0].equalsIgnoreCase(name)) {
					return line;
				}
			}
		}
		return null;
	}

	private static String[] joinChatRoom(String username, Scanner scnr) {
		System.out.print("Enter the chat room name: ");
		String chatRoomName = scnr.nextLine();

		try (BufferedReader br = new BufferedReader(new FileReader("chatrooms.txt"))) {
			String line;
			while ((line = br.readLine()) != null) {
				String[] chatRoomInfo = line.split(",");
				if (chatRoomInfo[0].equals(chatRoomName)) {
					return new String[] { chatRoomInfo[1], chatRoomInfo[2] };
				}
			}
		} catch (IOException e) {
			System.err.println("Error reading chat room information from file");
			e.printStackTrace();
		}

		System.out.println("Chat room not found");
		return null;
	}

	private static void startChat(String multicastIP, int port) {
		try {
			InetAddress group = InetAddress.getByName(multicastIP);
			MulticastSocket socket = new MulticastSocket(port);
			socket.joinGroup(group);

			// Thread to send messages
			Thread sendThread = new Thread(() -> {
				Scanner scnr = new Scanner(System.in);
				while (true) {
					System.out.print("Enter message: ");
					String message = scnr.nextLine();

					if (message.equalsIgnoreCase("/leave")) {
						break;
					} else if (message.equalsIgnoreCase("/commands")) {
						System.out.println("Available commands:\n/leave\n/commands");
					} else {
						String formattedMessage = name + ": " + message;
						byte[] buffer = formattedMessage.getBytes();
						DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, port);
						try {
							socket.send(packet);
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
				scnr.close();
			});

			// Thread to receive messages
			Thread receiveThread = new Thread(() -> {
				byte[] buffer = new byte[1024];
				DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
				while (true) {
					try {
						socket.receive(packet);
						String receivedMessage = new String(packet.getData(), 0, packet.getLength());

						// Check if the received message is from the current user
						if (!receivedMessage.startsWith(name + ": ")) { // Changed 'username' to 'name'
							System.out.println("\n" + receivedMessage);
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			});

			sendThread.start();
			receiveThread.start();

			sendThread.join();
			socket.leaveGroup(group);
			socket.close();
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	private static void updateAccountInfo(Scanner scnr) {
		System.out.print("Enter your current username: ");
		String username = scnr.nextLine();
		System.out.print("Enter your current password: ");
		String password = scnr.nextLine();
		try (Scanner fileScanner = new Scanner(new File(CREDENTIALS_FILE))) {
			while (fileScanner.hasNextLine()) {
				String[] line = fileScanner.nextLine().split(",");
				if (line[0].equals(username) && line[1].equals(password)) {
					System.out.println("Enter '1' to change your username, or '2' to change your password:");
					String option = scnr.nextLine();
					if (option.equals("1")) {
						boolean usernameExists;
						String newUsername;
						do {
							System.out.print("Enter your new username: ");
							newUsername = scnr.nextLine();
							usernameExists = checkUsernameExists(newUsername);
							if (usernameExists) {
								System.out.println("Username already exists. Please try again.");
							}
						} while (usernameExists);
						// Update the credentials file
						Path path = Paths.get(CREDENTIALS_FILE);
						List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
						for (int i = 0; i < lines.size(); i++) {
							String[] fields = lines.get(i).split(",");
							if (fields[0].equals(username)) {
								lines.set(i, newUsername + "," + password);
							}
						}
						Files.write(path, lines, StandardCharsets.UTF_8);
						name = newUsername;
						System.out.println("Username updated successfully.");
					} else if (option.equals("2")) {
						System.out.print("Enter your new password: ");
						String newPassword = scnr.nextLine();
						// Update the credentials file
						Path path = Paths.get(CREDENTIALS_FILE);
						List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
						for (int i = 0; i < lines.size(); i++) {
							String[] fields = lines.get(i).split(",");
							if (fields[0].equals(username)) {
								lines.set(i, username + "," + newPassword);
							}
						}
						Files.write(path, lines, StandardCharsets.UTF_8);
						System.out.println("Password updated successfully.");
					} else {
						System.out.println("Invalid option.");
					}
					return;
				}
			}
			System.out.println("Incorrect username or password. Please try again.");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// This method checks if a username already exists in the credentials file
	private static boolean checkUsernameExists(String username) throws IOException {
		try (Scanner fileScanner = new Scanner(new File(CREDENTIALS_FILE))) {
			while (fileScanner.hasNextLine()) {
				String[] line = fileScanner.nextLine().split(",");
				if (line[0].equals(username)) {
					return true;
				}
			}
		}
		return false;
	}

	// This method prompts the user to register a new username and password
	private static final String CREDENTIALS_FILE = "credentials.txt";

	private static void register(Scanner scnr) {
		try (FileWriter fw = new FileWriter(CREDENTIALS_FILE, true);
				BufferedWriter bw = new BufferedWriter(fw);
				PrintWriter pw = new PrintWriter(bw)) {
			System.out.print("Enter a new username: ");
			String username = scnr.nextLine();
			if (checkUsernameExists(username)) {
				System.out.println("Username already exists. Please try again.");
				return; // return without registering the user
			}
			System.out.print("Enter a password: ");
			String password = scnr.nextLine();
			pw.println(username + "," + password);
			System.out.println("Registration successful. You may now log in.");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// This method prompts the user to enter their username and password to log in
	private static String login(Scanner scnr) {
		try (Scanner fileScanner = new Scanner(new File(CREDENTIALS_FILE))) {
			System.out.print("Enter your username: ");
			String username = scnr.nextLine();
			if (!checkUsernameExists(username)) {
				System.out.println("Username not found. Please try again.");
				return null;
			}
			System.out.print("Enter your password: ");
			String password = scnr.nextLine();
			while (fileScanner.hasNextLine()) {
				String[] line = fileScanner.nextLine().split(",");
				if (line[0].equals(username) && line[1].equals(password)) {
					return username;
				}
			}
			System.out.println("Incorrect password. Please try again.");
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	// This class reads incoming messages from the group chat
	static class ReadThread implements Runnable {
		private MulticastSocket socket;
		private InetAddress group;
		private int port;
		private static final int MAX_LEN = 1000;

		ReadThread(MulticastSocket socket, InetAddress group, int port) {
			this.socket = socket;
			this.group = group;
			this.port = port;
		}

		@Override
		public void run() {
			while (!finished) {
				byte[] buffer = new byte[ReadThread.MAX_LEN];
				DatagramPacket datagram = new DatagramPacket(buffer, buffer.length, group, port);
				String message;
				try {
					socket.receive(datagram);
					message = new String(buffer, 0, datagram.getLength(), "UTF-8");
					if (message.startsWith("/")) {
						// If the message starts with a slash, it's a command
						String command = message.substring(1);
						handleCommand(command);
					} else {
						// Otherwise, treat it as a regular chat message
						if (!message.startsWith(GroupChat.name)) {
							// Add the message to the chat history
							chatHistory.add(message);
							// Save the chat history to a file
							try (FileWriter fw = new FileWriter("chat_history.txt", true);
									BufferedWriter bw = new BufferedWriter(fw);
									PrintWriter out = new PrintWriter(bw)) {
								out.println(message);
							} catch (IOException e) {
								System.err.println("Error saving chat history to file");
								e.printStackTrace();
							}
							System.out.println(message);
						}
					}
				} catch (IOException e) {
					System.out.println("Chatbox closed!");
				}
			}
			// Print the chat history after finishing the while loop
			System.out.println("Chat history:");
			for (String message : chatHistory) {
				System.out.println(message);
			}
		}

		private void handleCommand(String command) {
			switch (command) {
			case "Commands":
				System.out.println("System Commands:");
				System.out.println("To leave: /Leave");
				break;
			case "Clear":
				chatHistory.clear();
				System.out.println("Chat history cleared.");
				break;
			default:
				System.out.println("Invalid command.");
				break;
			}
		}
	}
}
