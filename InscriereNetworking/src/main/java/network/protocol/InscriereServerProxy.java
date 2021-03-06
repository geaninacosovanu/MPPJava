package network.protocol;

import model.Inscriere;
import model.Participant;
import model.Proba;
import model.User;
import network.dto.InscriereDTO;
import network.dto.ParticipantDTO;
import services.IInscriereObserver;
import services.IInscriereService;
import services.InscriereServiceException;

import services.dto.ParticipantProbeDTO;
import services.dto.ProbaDTO;
import validator.ValidationException;

import javax.jws.soap.SOAPBinding;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class InscriereServerProxy implements IInscriereService {
    private String host;
    private int port;

    private IInscriereObserver client;
    private ObjectInputStream input;
    private ObjectOutputStream output;
    private Socket connection;

    private BlockingQueue<Response> qresponses;
    private volatile boolean finished;

    public InscriereServerProxy(String host, int port) throws InscriereServiceException {
        this.host = host;
        this.port = port;
        qresponses = new LinkedBlockingQueue<>();
        initializeConnection();

    }

    @Override
    public void logout(User u, IInscriereObserver client) throws InscriereServiceException {
        Request req=new Request.Builder().type(RequestType.LOGOUT).build();
        sendRequest(req);
        closeConnection();
    }

    @Override
    public boolean login(String username, String parola, IInscriereObserver client) throws InscriereServiceException {

        User user = new User(username, parola);
        Request request = new Request.Builder().type(RequestType.LOGIN).data(user).build();
        sendRequest(request);
        boolean exist = false;
        Response response = readResponse();
        if (response.getType() == ResponseType.OK) {
            exist = (boolean) response.getData();
            this.client = client;
        }
        if (response.getType() == ResponseType.ERROR) {
            String err = response.getData().toString();
            closeConnection();
            throw new InscriereServiceException(err);
        }
        return exist;
    }

    @Override
    public List<ProbaDTO> getAllProba() throws InscriereServiceException {
        Request request = new Request.Builder().type(RequestType.GET_ALLPROBEDTO).build();
        sendRequest(request);
        Response response = readResponse();
        if (response.getType() == ResponseType.ERROR) {
            String err = response.getData().toString();
            throw new InscriereServiceException(err);
        }
        List<ProbaDTO> all = (List<ProbaDTO>) response.getData();
        return all;

    }


    @Override
    public List<ParticipantProbeDTO> getParticipanti(Integer idProba) throws InscriereServiceException {
        Request request = new Request.Builder().type(RequestType.GET_PARTICIPANTIDTO).data(idProba).build();
        sendRequest(request);
        Response response = readResponse();
        if (response.getType() == ResponseType.ERROR) {
            String err = response.getData().toString();
            throw new InscriereServiceException(err);
        }
        List<ParticipantProbeDTO> all = (List<ParticipantProbeDTO>) response.getData();
        return all;
    }

    @Override
    public void saveInscriere(String nume, Integer varsta, List<Proba> probe, boolean existent) throws ValidationException, InscriereServiceException {
        Request request = new Request.Builder().type(RequestType.ADD_INSCRIERE).data(new InscriereDTO(nume, varsta, probe, existent)).build();
        sendRequest(request);
        Response response = readResponse();
        if (response.getType() == ResponseType.ERROR) {
            String err = response.getData().toString();
            throw new InscriereServiceException(err);
        }


    }


    @Override
    public Participant getParticipant(String nume, Integer varsta) throws InscriereServiceException {
        Request request = new Request.Builder().type(RequestType.ADD_INSCRIERE).data(new ParticipantDTO(nume, varsta)).build();
        sendRequest(request);
        Response response = readResponse();
        if (response.getType() == ResponseType.ERROR) {
            String err = response.getData().toString();
            throw new InscriereServiceException(err);
        } else return (Participant) response.getData();
    }

    private void closeConnection() {
        finished = true;
        try {
            input.close();
            output.close();
            connection.close();
            client = null;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendRequest(Request request) throws InscriereServiceException {
        try {
            output.writeObject(request);
        } catch (IOException e) {
            throw new InscriereServiceException("Error sending object" + e);
        }
    }

    private Response readResponse() throws InscriereServiceException {
        Response response = null;
        try {
            response = qresponses.take();

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return response;
    }

    private void initializeConnection() throws InscriereServiceException {
        try {
            connection = new Socket(host, port);
            output = new ObjectOutputStream(connection.getOutputStream());
            output.flush();
            input = new ObjectInputStream(connection.getInputStream());
            finished = false;
            startReader();
        } catch (IOException e) {
            throw new InscriereServiceException(e.getMessage());
        }
    }

    private void handleUpdate(Response response) {
        if (response.getType() == ResponseType.NEW_INSCRIERE) {

            try {
                client.inscriereAdded();
            } catch (InscriereServiceException e) {
                e.printStackTrace();
            }
        }

    }

    private boolean isUpdate(Response response) {
        return response.getType() == ResponseType.NEW_INSCRIERE;
    }

    private void startReader() {
        Thread tw = new Thread(new ReaderThread());
        tw.start();
    }

    private class ReaderThread implements Runnable {
        public void run() {
            while (!finished) {
                try {
                    Object response = input.readObject();
                    System.out.println("response received " + response);
                    if (isUpdate((Response) response)) {
                        handleUpdate((Response) response);
                    } else {

                        try {
                            qresponses.put((Response) response);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                } catch (IOException e) {
                    System.out.println("Reading error " + e);
                } catch (ClassNotFoundException e) {
                    System.out.println("Reading error " + e);
                }
            }
        }
    }

}
