package bean;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.SecretKey;
import javax.ejb.Singleton;
import client.Client;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Iterator;
import javax.crypto.KeyGenerator;

@Singleton
public class ClientData implements ClientDataLocal {

    // Client maps
    private Map<String, SecretKey> requests;
    private Map<String, Contact> clients;
    // PKA Keys
    private PrivateKey privateKey;
    private PublicKey publicKey;

    public ClientData() {

        requests = new HashMap<>();
        clients = new HashMap<>();
        // Populate keys if not yet done
        if (publicKey == null && privateKey == null) {
            generateKeys();
        }
    }

    @Override
    public synchronized String getAllNumbers(String mobile, String request) {

        System.out.println("Request for all numbers from: " + mobile);
        
        String numbers = "";

        // Is client active and valid?
        if (clients.containsKey(mobile)) {
                
            // Get pub key
            PublicKey pubKey = clients.get(mobile).getPublicKey();
            byte[] data = Utility.doubleDecryptData(request, privateKey, pubKey);

            if(mobile.equals(new String(data))) {

                // Get client keys
                Iterator<String> clientKeys = clients.keySet().iterator();
                // Get client numbers data
                while (clientKeys.hasNext()) {
                    numbers += clientKeys.next();

                    if (clientKeys.hasNext()) {
                        numbers += ",";
                    }
                }
                // Add RSA encryption here
                String encryptedData = Utility.doubleEncryptData(numbers.getBytes(), pubKey, privateKey);
                // Base64 encode for transport
                String encoded = Utility.encodeToBase64(encryptedData.getBytes());

                        
                System.out.println("Sending all numbers to requester: " + mobile);
                return encoded;
            }
            return numbers;
        }
        
        // Return empty data, client was not active client
        return numbers;
    }

    @Override
    public synchronized String getPublicKey(String mobile, String request) {

        String key = null;

        if (clients.containsKey(mobile)) {

            PublicKey pubKey = clients.get(mobile).getPublicKey();
            byte[] cipherBytes = Utility.doubleDecryptData(request, privateKey, pubKey);
            String contactMob = new String(cipherBytes);
            System.out.println("Contact Requested: " + contactMob);
            if (clients.containsKey(contactMob)) {
                // Get recipient key
                PublicKey contactKey = clients.get(contactMob).getPublicKey();
                
                System.out.println("Key Size: " + contactKey.getEncoded().length);
                
                String encryptedKey = Utility.doubleEncryptData(contactKey.getEncoded(), pubKey, privateKey);
                // Encode for transport
                key = Utility.encodeToBase64(encryptedKey.getBytes());
                
                return key;
            } else {
                System.out.println("Contact Key Isnt registered");
            }
            return key;
        } else {
            System.out.println("Client Key isnt registered");
        }
        // Return nothing, client doesnt exist
        return key;
    }

    @Override
    public synchronized String joinServer(String mobile, String request) {

        System.out.println("Request to Join: " + mobile);
        System.out.println("Request: " + request);

        // Ensure client has requested to join and is a valid sender 
        if (requests.containsKey(mobile)) {
            try {
                // Decode from transport
                byte[] decodedBytes = Utility.decodeFromBase64(request);
                // Get one time key for mobile number
                SecretKey ephemeral = requests.get(mobile);
                
                byte[] decryptBytes = Utility.decryptAES(ephemeral, decodedBytes);

                // Get data from ciphertext
                String[] data = new String(decryptBytes).split("---");
                String phoneNum = data[0];
                byte[] nonceBytes = Base64.getDecoder().decode(data[1].getBytes());
                byte[] pubKeyBytes = Base64.getDecoder().decode(data[2].getBytes());

                // Build public ket from supplied data
                PublicKey clientPubKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(pubKeyBytes));
                
                // Open nonceBytes to get nonce data
                Cipher rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                rsaCipher.init(Cipher.DECRYPT_MODE, privateKey);
                byte[] nonce = rsaCipher.doFinal(nonceBytes);

                // Does nonce match?
                if (new String(nonce).equals(phoneNum)) {
                    // Remove from temp requests
                    requests.remove(phoneNum);
                    // Add to active clients
                    clients.put(phoneNum, new Contact(ephemeral, clientPubKey));
                    
                    if(clients.containsKey(phoneNum))
                        return "Success";
                }
            } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException | InvalidKeySpecException ex) {
                System.out.println("Join Server Failed: " + ex.getMessage());
            }
        }

        return "Fail";
    }

    @Override
    public synchronized String requestOneTimeKey(String mobile) {

        // Generate random one time password
        String password = generateOneTimePassword();
        SecretKey ephemeral = generateEphemeral(password);

        String ephemeralBase64 = Utility.encodeToBase64(ephemeral.getEncoded());
        // Add to requests mapping
        requests.put(mobile, ephemeral);
        System.out.println("Request to join");
        System.out.println("Phone Number: " + mobile);
        System.out.println("Password: " + ephemeralBase64);
        return ephemeralBase64;
    }

    private void generateKeys() {

        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair keyPair = kpg.generateKeyPair();
            publicKey = keyPair.getPublic();
            privateKey = keyPair.getPrivate();
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private String generateOneTimePassword() {

        Random rand = new Random();

        String[] fruits = {"Pineapple", "Raspberry", "Passionfruit", "Tangerine", "Coconut", "Avocado",
            "Rockmelon", "Banana", "Kiwifruit", "Watermelon"};
        int randNum = rand.nextInt(9999 - 1000) + 1000;

        // Generate one time key
        return fruits[rand.nextInt(fruits.length)] + randNum;
    }

    private SecretKey generateEphemeral(String password) {

        SecretKey key = null;

        try {
            // generate a secret key for AES
            KeyGenerator kg = KeyGenerator.getInstance("AES");
            kg.init(128); // 128-bit key used for AES
            key = kg.generateKey();

        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
        }

        return key;
    }

    @Override
    public synchronized String getPkaPublicKey() {

        // Populate keys if not populated
        if (publicKey == null && privateKey == null) {
            generateKeys();
        }
        // Base 64 encode key
        String pubkey = Utility.encodeToBase64(publicKey.getEncoded());
        // Return base64 & HTML encoded pka pub key
        return pubkey;
    }

    @Override
    public String requestImageKey(String mobile, String request) {
        
        String key = null;
        
        if(clients.containsKey(mobile)) {
            
            Contact client = clients.get(mobile);
            byte[] decryptedData = Utility.doubleDecryptData(request, privateKey, client.getPublicKey());
            
            String message = new String(decryptedData);
            
            if(message.equals("key request")) {
                // Produce and get key
                SecretKey fileKey = client.requestFileKey();
                // Encode for transport
                String encodedKey = Utility.encodeToBase64(fileKey.getEncoded());
                // Encrypt key
                String encrypted = Utility.doubleEncryptData(encodedKey.getBytes(), client.getPublicKey(), privateKey);
                // Encode
                key = Utility.encodeToBase64(encrypted.getBytes());
            }
        }
        
        return key;
    }

    @Override
    public boolean processUpload(String mobile, String data) {
        boolean success = false;
        
        if(clients.containsKey(mobile)) {
            Contact client = clients.get(mobile);
        }
        
        return success;
    }

    @Override
    public String processDownload(String mobile, String request) {
       String data = null;
       
       if(clients.containsKey(mobile)) {
           
       }
       
       return data;
    }

    @Override
    public String getFileNames(String mobile, String request) {
        
        String data = null;
        
        if(clients.containsKey(mobile)) {
            
            // Get client
            Contact client = clients.get(mobile);
            // Process request (decrypt and read)
            
            // If valid, get file names and concat as data string, return
        }
        
        return data;
    }
}
