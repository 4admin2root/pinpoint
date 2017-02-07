/*
 * Copyright 2014 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.navercorp.pinpoint.web.alarm;

import com.navercorp.pinpoint.web.alarm.checker.AlarmChecker;
import com.navercorp.pinpoint.web.alarm.vo.Rule;
import com.navercorp.pinpoint.web.service.UserGroupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.io.*;
import java.util.List;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeUtility;
/**
 * @author minwoo.jung
 * changed by jason.lui 2017-1-23
 */
public class XzxMessageSender implements AlarmMessageSender {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private UserGroupService userGroupService;
    @Value("#{alarmProps['smssend.scriptfile'] ?: ''}")
    private String smssend_scriptfile;
    @Value("#{alarmProps['email.ssl.smtpserver'] ?: ''}")
    private String email_ssl_smtpserver;
    @Value("#{alarmProps['email.ssl.smtpport'] ?: ''}")
    private Integer email_ssl_smtpport;
    @Value("#{alarmProps['email.username'] ?: ''}")
    private String email_username;
    @Value("#{alarmProps['email.password'] ?: ''}")
    private String email_password;
    @Override
    public void sendSms(AlarmChecker checker, int sequenceCount) {
        List<String> receivers = userGroupService.selectPhoneNumberOfMember(checker.getuserGroupId());

        if (receivers.size() == 0) {
            return;
        }



        for (String message : checker.getSmsMessage()) {
            logger.debug("send SMS : {}", message);
            try
            {
                Runtime rt = Runtime.getRuntime();
                for (String phonenum : receivers) {

                    logger.debug("send sms script: {}",new String []{smssend_scriptfile,phonenum,message});
                    Process proc = rt.exec(new String []{smssend_scriptfile,phonenum,message});
                    InputStream stderr = proc.getErrorStream();
                    InputStream stdin = proc.getInputStream();
                    InputStreamReader isr = new InputStreamReader(stderr);
                    InputStreamReader isi = new InputStreamReader(stdin);
                    BufferedReader br = new BufferedReader(isr);
                    BufferedReader bi = new BufferedReader(isi);

                    String temp = null ;
                    while((temp=br.readLine())!=null) {
                        logger.debug("br readline is: ",temp);
                    }
                    while((temp=bi.readLine())!=null) {
                        logger.debug("bi readline is: ",temp);
                    }
                    int exitVal = proc.waitFor();
                    System.out.println("exitval is: "+exitVal);
                    isr.close();
                    isi.close();
                    br.close();
                    bi.close();
                    logger.debug("Process exitValue: ", Integer.toString(exitVal));
                }
            } catch (Throwable t)
            {
                t.printStackTrace();
            }


        }
    }

    @Override
    public void sendEmail(AlarmChecker checker, int sequenceCount) {
        List<String> receivers = userGroupService.selectEmailOfMember(checker.getuserGroupId());
        String SSL_FACTORY = "javax.net.ssl.SSLSocketFactory";
        Properties properties = new Properties();
        properties.put("mail.smtp.host", email_ssl_smtpserver);
        properties.put("mail.smtp.auth", "true");
        properties.setProperty("mail.smtp.socketFactory.class", SSL_FACTORY);
        properties.setProperty("mail.smtp.socketFactory.fallback", "false");

        properties.put("mail.smtp.port", email_ssl_smtpport);
        properties.setProperty("mail.smtp.socketFactory.port", email_ssl_smtpport.toString());


//        Session session = Session.getInstance(properties,new javax.mail.Authenticator() {
//            protected PasswordAuthentication getPasswordAuthentication() {
//                return new PasswordAuthentication(email_username, email_password);
//            }
//        });
        Session session = Session.getDefaultInstance(properties);
        session.setDebug(true);
        MimeMessage msg = new MimeMessage(session);

        if (receivers.size() == 0) {
            return;
        }

        String message = checker.getEmailMessage();
        String chkname = checker.getRule().getCheckerName();
        try {
            Address address = new InternetAddress(email_username);
            msg.setFrom(address);
            msg.setSubject("ALARM FROM PINPOINT:"+chkname);
            msg.setText(checker.getEmailMessage());
            msg.setSentDate(new Date());
            //msg.saveChanges();
            ArrayList <Address> list = new ArrayList<Address>();
            for (String mailaddr : receivers) {
                 list.add(new InternetAddress(mailaddr));
            }
            Address[] array = new Address[list.size()];
            Address[] toAddress=list.toArray(array);

            logger.info("send email : {}", message);

            Transport transport = session.getTransport("smtp");
            transport.connect(email_ssl_smtpserver, email_username, email_password);
            msg.saveChanges();
            transport.sendMessage(msg,toAddress);
            transport.close();


        }catch (Exception e) {
            e.printStackTrace();
        }

    }
    }


