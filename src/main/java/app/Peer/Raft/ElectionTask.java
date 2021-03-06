package app.Peer.Raft;

import app.Protocols.RaftProtocol.ElectionProtocol;

public class ElectionTask {
    /**
     This class is used to react to an election request.
     **/
    private String elector;
    private String candidate;
    private boolean vote;
    private int term = 0;
    public ElectionTask(String elector, int term){
        this.elector = elector;
        this.term = term;
    }

    public void vote(String candidate, boolean vote){
        try {
            ElectionProtocol msg = new ElectionProtocol(vote, this.term, RaftController.getInstance().getMyName(), candidate);
            System.out.println("New vote: "+msg);
            RaftController.getInstance().sendMsg(msg, candidate);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
