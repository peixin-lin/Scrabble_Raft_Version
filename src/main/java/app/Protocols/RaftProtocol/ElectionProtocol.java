package app.Protocols.RaftProtocol;

import app.Protocols.ScrabbleProtocol;

public class ElectionProtocol extends ScrabbleProtocol {
    private boolean vote = false;
    private int term;
    public boolean isVote(){return this.vote;}
    public ElectionProtocol(){
        super.setTAG("ElectionProtocol");
    }
    public ElectionProtocol(boolean vote, int term){
        super.setTAG("ElectionProtocol");
        this.vote = vote;
        this.term = term;
    }
}