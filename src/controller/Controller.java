package controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import model.ARP;
import model.Util;
import org.jnetpcap.Pcap;
import org.jnetpcap.PcapHeader;
import org.jnetpcap.PcapIf;
import org.jnetpcap.nio.JBuffer;
import org.jnetpcap.nio.JMemory;
import org.jnetpcap.packet.JRegistry;
import org.jnetpcap.packet.PcapPacket;
import org.jnetpcap.protocol.lan.Ethernet;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.ResourceBundle;

public class Controller implements Initializable {

    @FXML
    private ListView<String> networkListView;

    @FXML
    private TextArea textArea;

    @FXML
    private Button pickButton;

    @FXML
    private TextField myIP;

    @FXML
    private TextField senderIP;

    @FXML
    private TextField targetIP;

    @FXML
    private Button getMACButton;

    ObservableList<String> networkList = FXCollections.observableArrayList();

    private ArrayList<PcapIf> allDevs = null;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        allDevs = new ArrayList<PcapIf>();
        StringBuilder errbuf = new StringBuilder();
        int r = Pcap.findAllDevs(allDevs, errbuf);
        if (r == Pcap.NOT_OK || allDevs.isEmpty()) {
            textArea.appendText("네트워크장치를 찾을 수 없습니다.\n" + errbuf.toString() + "\n");
        }
        textArea.appendText("네트워크 장치를 찾았습니다.\n원하시는 장치를 선택하세요\n");
        for(PcapIf device : allDevs) {
            networkList.add(device.getName() + " " +
                    ((device.getDescription() != null) ? device.getDescription() : "설명 없음" ));
        networkListView.setItems(networkList);
        }
    }

    public void networkPickAction() {
        if (networkListView.getSelectionModel().getSelectedIndex() < 0) {
            return;
        }
        Main.device = allDevs.get(networkListView.getSelectionModel().getSelectedIndex());
        networkListView.setDisable(true);
        pickButton.setDisable(true);

        int snaplen = 64 * 1024;
        int flags = Pcap.MODE_NON_PROMISCUOUS;
        int timeout = 1;

        StringBuilder errbuf = new StringBuilder();
        Main.pcap = Pcap.openLive(Main.device.getName(), snaplen, flags, timeout, errbuf);

        if(Main.pcap == null){
            textArea.appendText("네트워크 장치를 열 수 없습니다.\n" + errbuf.toString() + "\n");
            return;
        }
        textArea.appendText("장치 선택 : " + Main.device.getName() + "\n");
        textArea.appendText("네트워크장치를 활성화했습니다.\n");
    }

    public void getMACAction() {
        if(!pickButton.isDisable()) {
            textArea.appendText("네트워크 장치를 먼저 선택해주세요.\n");
            return;
        }

        ARP arp = new ARP();
        Ethernet eth = new Ethernet();
        PcapHeader header = new PcapHeader(JMemory.POINTER);
        JBuffer buf = new JBuffer(JMemory.POINTER);
        ByteBuffer buffer;

        int id = JRegistry.mapDLTToId(Main.pcap.datalink());

        try {
            Main.myMAC = Main.device.getHardwareAddress();
            Main.myIP = InetAddress.getByName(myIP.getText()).getAddress();
            Main.senderIP = InetAddress.getByName(senderIP.getText()).getAddress();
            Main.targetIP = InetAddress.getByName(targetIP.getText()).getAddress();
        } catch (IOException e) {
            textArea.appendText("IP 주소가 잘못되었습니다.\n");
            return;
        }
        // 입력된 곳을 비활성화한다.
        myIP.setDisable(true);
        senderIP.setDisable(true);
        targetIP.setDisable(true);
        getMACButton.setDisable(true);

        arp = new ARP();
        // 다른 사용자의 맥주소를 얻어오기 위함 (ARP Reqest 패킷 target에게 보내기)
        arp.makeARPRequest(Main.myMAC, Main.myIP, Main.targetIP);
        buffer = ByteBuffer.wrap(arp.getPacket());
        if(Main.pcap.sendPacket(buffer) != Pcap.OK){
            System.out.println(Main.pcap.getErr());
        }
        textArea.appendText("타겟에게 ARP Request를 보냈습니다!\n" +
                Util.bytesToString(arp.getPacket()) + "\n");

        Main.targetMAC = new byte[6];
        while (Main.pcap.nextEx(header, buf) != Pcap.NEXT_EX_NOT_OK) {
            PcapPacket packet = new PcapPacket(header, buf);
            packet.scan(id);
            byte[] sourceIP = new byte[4];
            System.arraycopy(packet.getByteArray(0, packet.size()), 28, sourceIP, 0, 4);
            // 원하는 reply 패킷인지 확인하는 구간
            if (packet.getByte(12) == 0x08 && packet.getByte(13) == 0x06
            && packet.getByte(20) == 0x00 && packet.getByte(21) == 0x02
            && Util.bytesToString(sourceIP).equals(Util.bytesToString(Main.targetIP))
            && packet.hasHeader(eth)) {
                // 2계층 MAC주소인지 확인하고 얻으면 if문을 빠져나간다.
                Main.targetMAC = eth.source();
                break;
            } else {
                continue;
            }
        }
        textArea.appendText("타겟 맥 주소: " +
                Util.bytesToString(Main.targetMAC) + "\n");
    }
}
