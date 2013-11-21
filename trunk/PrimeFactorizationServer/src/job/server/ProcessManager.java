package job.server;

import job.Job;
import job.client.ClientCallback;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author john
 */
public abstract class ProcessManager implements JobServer, Runnable {

    protected Map<UUID, Job> expired;
    protected Map<UUID, Session> sessions;
    protected boolean run;

    protected class Session {
        public UUID id;
        public ClientCallback client;
        public Map<UUID, Job> jobs;

        public Session(ClientCallback client) {
            this.client = client;
            id = UUID.randomUUID();
            jobs = new HashMap<UUID, Job>();
        }
    }

    public ProcessManager() {
        expired = new HashMap<UUID, Job>();
        sessions = new HashMap<UUID, Session>();
    }

    public synchronized UUID getSession(ClientCallback client) {
        Session session = new Session(client);
        sessions.put(session.id, session);
        Logger.getLogger(ProcessManager.class.getName()).info("created session " + session.id);
        return session.id;
    }

    @Override
    public synchronized void endSession(UUID id) throws SessionExpiredException {
        Session session = sessions.get(id);
        if (session != null) {
            for (UUID i : session.jobs.keySet()) {
                expired.put(i, session.jobs.get(i));
            }
            sessions.remove(session.id);
            Logger.getLogger(ProcessManager.class.getName()).info("ended session " + session.id);
        } else {
            throw new SessionExpiredException();
        }
    }
    
    protected synchronized void addJob(UUID session, Job job) {
        Session s = sessions.get(session);
        s.jobs.put(job.getId(), job);
    }
    
    protected synchronized void stopJobs() {
        for(UUID session : sessions.keySet()) {
            Session s = sessions.get(session);
            try {
                s.client.stopJobs();
            } catch (RemoteException ex) {
                try {
                    endSession(session);
                } catch (SessionExpiredException ex1) {
                    Logger.getLogger(ProcessManager.class.getName()).log(Level.SEVERE, null, ex1);
                }
            }
            for(UUID jobid : s.jobs.keySet()) {
                Job j = s.jobs.get(jobid);
                expired.put(jobid, j);
            }
        }
    }

    public synchronized void stop() {
        run = false;
    }

    public void run() {
        run = true;

        while (run) {
            synchronized (this) {
                List<UUID> endSessions = new ArrayList<UUID>();
                for (UUID id : sessions.keySet()) {
                    ClientCallback c = sessions.get(id).client;
                    try {
                        c.status();
                    } catch (Exception ex) {
                        endSessions.add(id);
                        Logger.getLogger(ProcessManager.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                for(UUID id : endSessions) {
                    try {
                        endSession(id);
                    } catch (SessionExpiredException ex) {
                        Logger.getLogger(ProcessManager.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
            try {
                Thread.sleep(5 * 60 * 1000);
            } catch (InterruptedException ex) {
                Logger.getLogger(ProcessManager.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        for (UUID id : sessions.keySet()) {
            try {
                sessions.get(id).client.stopJobs();
            } catch (RemoteException ex) {
                Logger.getLogger(ProcessManager.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
}
