package agent;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.UnreadableException;

import java.awt.EventQueue;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import javax.swing.SwingUtilities;

import misc.Logger;
import pojo.Song;
import pojo.SongRequestInfo;
import pojo.SongSellInfo;
import util.F.Tuple;
import view.ProviderView;

public class MusicProvider extends Agent {

  final private Set<SongSellInfo> songList = new HashSet<SongSellInfo>();
  private MusicProvider agent = this;
  private ProviderView ui;
  
  @Override
  public void setup() {
    
    /* Broadcast this agent */
    ServiceDescription sd = new ServiceDescription();
    sd.setType("MUSIC-DISCOVERY");
    sd.setName(agent.getName());
    
    DFAgentDescription df = new DFAgentDescription();
    df.addServices(sd);
    
    try {
      DFService.register(this, df);
      Logger.info(agent, "Registering to DF agent...");
    } catch (FIPAException e) {
      Logger.error(agent, e, "Couldn't register to DF agent!");
    }
    
    EventQueue.invokeLater(new Runnable() {
      @Override
      public void run() {
        Logger.info(agent, "Creating Music Provider UI...");
        try {
          ui = new ProviderView(agent);
          ui.setVisible(true);
        } catch (Exception e) {
          Logger.error(agent, e, "Couldn't create UI!");
        }
      }
    });
    
    addBehaviour(new CheckBuyerMessages());
  }

  public ProviderView getUi() {
    return ui;
  }
  
  private class CheckBuyerMessages extends CyclicBehaviour {

    @Override
    public void action() {
      Logger.info(agent, "Waiting for 'Buy Song' and 'Query Song' requests...");
      ACLMessage msg = this.myAgent.receive();
      if (msg == null) { this.block(); return; }
      
      try {
        Object something = msg.getContentObject();
        if(something.getClass().equals(SongRequestInfo.class)) {
          agent.addBehaviour(agent.new SongSearch((SongRequestInfo)something, msg));
          return;
        }
        
        if(something.getClass().equals(SongSellInfo.class)) {
          agent.addBehaviour(agent.new BuySong((SongSellInfo)something, msg));
          return;
        }
        
        Logger.warn(agent, "Got an unexpected message from %s!", msg.getSender().getName());
      } catch (UnreadableException e) {
        Logger.error(agent, e, "Couldn't parse and read the message!");
      }
    }
  }
  
  private class SongSearch extends OneShotBehaviour {

    SongRequestInfo sri;
    ACLMessage msg;
    public SongSearch(SongRequestInfo sri, ACLMessage msg) {
      this.sri = sri;
      this.msg = msg;
    }

    @Override
    public void action() {
      Logger.info(agent, "Searching song with given criteria...");
      HashSet<SongSellInfo> tbReturned = new HashSet<SongSellInfo>();
      
      for(SongSellInfo ssi: agent.songList) {
        Song song = ssi.getSong();
        
        if(sri.genre != null && !song.getGenre().equals(sri.genre)) {
          continue;
        }
//        if(sri.maxPricePerSong < ssi.getPrice()) {
//          continue;
//        }
//        if(sri.minRating > ssi.getAvgRating()) {
//          continue;
//        }
        
        tbReturned.add(ssi.clone());
      }
      
      ACLMessage reply = msg.createReply();

      if(tbReturned.size() == 0) {
        reply.setPerformative(ACLMessage.REFUSE);
      } else {
        reply.setPerformative(ACLMessage.PROPOSE);
      }
      try {
        reply.setContentObject(tbReturned);
        this.myAgent.send(reply);
      } catch (Exception e) {
        Logger.error(agent, e, "Couldn't sent search results.");
      }
    }
  }
  
  private class BuySong extends OneShotBehaviour {

    SongSellInfo ssi;
    ACLMessage msg;
    public BuySong(SongSellInfo ssi, ACLMessage msg) {
      this.ssi = ssi;
      this.msg = msg;
    }

    @Override
    public void action() {
      
      ACLMessage reply = msg.createReply();
      if(!agent.songList.contains(ssi)) {
        reply.setPerformative(ACLMessage.REFUSE);
      } else {
        reply.setPerformative(ACLMessage.PROPOSE);
      }

      Tuple<String, SongSellInfo> urlRequest = new Tuple<String, SongSellInfo>(generateRandomUrl(), ssi);
      try {
        reply.setContentObject(urlRequest);
        this.myAgent.send(reply);
        
        Logger.info(agent, "Song sold. Seller agent: %s Song Info: %s - %s", msg.getSender().getName(), ssi.getSong().getArtist(), ssi.getSong().getName());
        
        Runnable addIt = new Runnable() { 
          @Override
          public void run() {
            ui.addBuyedItem("@" + msg.getSender().getLocalName() + " [" + ssi.getSong().getArtist() + " - " + ssi.getSong().getName() + "]");
          }
        };
       
        SwingUtilities.invokeLater(addIt);
      } catch (Exception e) {
        Logger.error(agent, e, "Couldn't sent song purchase response.");
      }
    }

    private String generateRandomUrl() {
      return "http://musicdownload.com/song/" + new Random().nextInt(9999);
    }
  }
  
  public class RemoveSong extends OneShotBehaviour {
    
    private SongSellInfo ssi;
    public RemoveSong(SongSellInfo ssi) {
      this.ssi = ssi;
    }
    
    @Override
    public void action() {
      agent.songList.remove(ssi);
    }
  }
  
  public class AddSong extends OneShotBehaviour {

    private SongSellInfo ssi;
    public AddSong(SongSellInfo ssi) {
      this.ssi = ssi;
    }
    
    @Override
    public void action() {
      agent.songList.add(ssi);
    }
  }
  
  public class ShutdownAgent extends OneShotBehaviour {

    @Override
    public void action() {
      try {
        DFService.deregister(agent);
      } catch (FIPAException e) {
        Logger.error(agent, e, "Agent couldn't be unregistered from DF.");
      }
      agent.doDelete();
    }
  }
}
