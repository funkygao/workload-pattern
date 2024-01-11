package io.github.workload.overloading;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.TimeUnit;

// http://www.sigcomm.org/node/2770
public class MulTFRCDemo extends JFrame {
    JButton button1;
    JLabel sLead, RLead, bLead, pLead, jLead, t_RTOLead, NLead, X_BpsLead;
    JTextField sInput, RInput, bInput, pInput, jInput, t_RTOInput, NInput, X_BpsOutput;

    public MulTFRCDemo() {
        setTitle("MulTFRC calculation");
        JPanel samling = new JPanel();
        samling.setLayout(new GridLayout(8, 2));
        sLead = new JLabel("  s, segment size (bytes):");
        samling.add(sLead);
        sInput = new JTextField(6);
        sInput.setEditable(true);
        samling.add(sInput);
        RLead = new JLabel("  R, round trip time (seconds):");
        samling.add(RLead);
        RInput = new JTextField(6);
        RInput.setEditable(true);
        samling.add(RInput);
        bLead = new JLabel(" b,  max # packets acked per TCP ack:");
        samling.add(bLead);
        bInput = new JTextField(6);
        bInput.setEditable(true);
        samling.add(bInput);
        pLead = new JLabel(" p, loss event rate (<0 - 1.0>):");
        samling.add(pLead);
        pInput = new JTextField(6);
        pInput.setEditable(true);
        samling.add(pInput);
        jLead = new JLabel(" j,  avg. # packets lost in a loss event:");
        samling.add(jLead);
        jInput = new JTextField(6);
        jInput.setEditable(true);
        samling.add(jInput);
        t_RTOLead = new JLabel("  t_RTO, retrans. timeout value (seconds):");
        samling.add(t_RTOLead);
        t_RTOInput = new JTextField(6);
        t_RTOInput.setEditable(true);
        samling.add(t_RTOInput);
        NLead = new JLabel("  N, # TFRC flows:");
        samling.add(NLead);
        NInput = new JTextField(6);
        NInput.setEditable(true);
        samling.add(NInput);
        X_BpsLead = new JLabel("  Calculated average tmit rate (bytes/sec):");
        samling.add(X_BpsLead);
        X_BpsOutput = new JTextField(6);
        X_BpsOutput.setEditable(false);
        samling.add(X_BpsOutput);

        Container wnd = getContentPane();
        wnd.setLayout(new BorderLayout());
        wnd.add(samling, BorderLayout.WEST);

        JPanel but = new JPanel();
        but.setLayout(new GridLayout(7, 1));
        button1 = new JButton("Show result");
        button1.addActionListener(new Computer());
        but.add(button1);

        wnd.add(but, BorderLayout.EAST);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBounds(200, 250, 700, 250);
        setVisible(true);
    }

    public double calculate(int s, double R, int b, double p, double j, double t_RTO, double N) {
        double x, af, a, z, q, X_Bps;
        if (N < 12) {
            af = N * (1 - Math.pow((1 - 1 / N), j));
        } else {
            af = j;
        }
        af = Math.max(Math.min(af, Math.ceil(N)), 1);
        a = p * b * af * (24 * N * N + p * b * af * Math.pow((N - 2 * af), 2));
        x = (af * p * b * (2 * af - N) + Math.sqrt(a)) / (6 * N * N * p);
        z = t_RTO * (1 + 32 * p * p) / (1 - p);
        q = Math.min(Math.min(2 * j * b * z / (R * (1 + 3 * N / j) * x * x), N * z / (x * R)), N);
        X_Bps = ((1 - q / N) / (p * x * R) + q / (z * (1 - p))) * s;
        return X_Bps;
    }

    class Computer implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            double svar = calculate(
                    Integer.parseInt(sInput.getText()),
                    Double.parseDouble(RInput.getText()),
                    Integer.parseInt(bInput.getText()),
                    Double.parseDouble(pInput.getText()),
                    Double.parseDouble(jInput.getText()),
                    Double.parseDouble(t_RTOInput.getText()),
                    Double.parseDouble(NInput.getText()));

            X_BpsOutput.setText(" " + svar);
        }
    }

    @Test
    @Disabled
    void demo() throws InterruptedException {
        new MulTFRCDemo();
        TimeUnit.HOURS.sleep(1);
    }

}
