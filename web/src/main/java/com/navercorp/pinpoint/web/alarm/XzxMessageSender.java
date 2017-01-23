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
import com.navercorp.pinpoint.web.service.UserGroupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
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

                    // TODO loop phonenum
                    logger.debug("send sms script: {}",smssend_scriptfile + " " + phonenum + " '" + message + "'");
                    Process proc = rt.exec(smssend_scriptfile + " " + phonenum + " '" + message + "'");
                    InputStream stderr = proc.getErrorStream();
                    InputStreamReader isr = new InputStreamReader(stderr);
                    BufferedReader br = new BufferedReader(isr);
                    // TODO script parameter values

                    int exitVal = proc.waitFor();
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
        properties.put("mail.smtp.socketFactory.class", SSL_FACTORY);  //使用JSSE的SSL socketfactory来取代默认的socketfactory
        properties.put("mail.smtp.socketFactory.fallback", "false");  // 只处理SSL的连接,对于非SSL的连接不做处理

        properties.put("mail.smtp.port", email_ssl_smtpport);
        properties.put("mail.smtp.socketFactory.port", email_ssl_smtpport);

        Session session = Session.getInstance(properties);
        session.setDebug(true);
        MimeMessage msg = new MimeMessage(session);

        if (receivers.size() == 0) {
            return;
        }

        String message = checker.getEmailMessage();
        try {
            Address address = new InternetAddress(email_username);
            msg.setFrom(address);
            msg.setSubject("ALARM FROM PINPOINT");
            BodyPart text = new MimeBodyPart();
            text.setText(checker.getEmailMessage());
            Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(text);
            msg.setContent(multipart);
            msg.saveChanges();
            for (String mailaddr : receivers)
            {
                logger.info("send email : {}", message);
                Address toAddress = new InternetAddress(mailaddr);
                msg.setRecipient(MimeMessage.RecipientType.TO, toAddress);
                Transport transport = session.getTransport("smtp");
                transport.connect(email_ssl_smtpserver, email_username, email_password);
                transport.sendMessage(msg, msg.getAllRecipients());
                transport.close();


            }
        }catch (Exception e) {
            e.printStackTrace();
        }

    }
    }


