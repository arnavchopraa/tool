package org.example.backend.services;

import org.example.backend.exceptions.EmailException;
import org.example.backend.importmodels.Association;
import org.example.backend.importmodels.Student;
import org.example.backend.importmodels.Submission;
import org.example.backend.models.SubmissionDB;
import org.example.backend.models.User;
import org.example.backend.importmodels.Coordinator;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.rowset.serial.SerialBlob;
import java.sql.Blob;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AccountService {
    private final EmailService emailService = new EmailService();
    private final UserService userService;
    private final SubmissionService submissionService;

    /**
     * Constructor for Account service, autowired by Spring.
     *
     * @param userService Repository used to save users.
     * @param submissionService Repository used to save submissions.
     */
    @Autowired
    public AccountService(UserService userService, SubmissionService submissionService) {
        this.userService = userService;
        this.submissionService = submissionService;
    }

    /**
     * Automatically creates accounts for coordinators, once files have been uploaded
     *
     * @param coordinators List of coordinators extracted from Excel file, generated by ProjectForum
     * @throws RuntimeException if email containing login credentials could not be sent.
     */
    public void createCoordinatorsAccounts(List<Coordinator> coordinators) throws RuntimeException, SQLException {
        for(Coordinator coordinator : coordinators) {
            String password = emailService.generateRandomCode();
            User optionalUser = userService.getUser(coordinator.getEmail());
            if(optionalUser == null) {
                User user = new User(coordinator.getEmail(), coordinator.getFullName(), PasswordHashingService.hashPassword(password), "supervisor");
                String emailSubject = "Your account has been created!";
                String emailContent = generateContent(coordinator.getEmail(), password);
                try {
                    emailService.sendEmail(coordinator.getEmail(), emailSubject, emailContent);
                } catch (EmailException e) {
                    throw new RuntimeException(e);
                }
                user = userService.addUser(user);
                createSubmissions(coordinator, user);
            } else {
                createSubmissions(coordinator, optionalUser);
            }
        }
    }

    /**
     * Automatically creates accounts for students, once files have been uploaded
     *
     * @param students List of students extracted from csv file, generated by Brightspace
     * @throws RuntimeException if email containing login credentials could not be sent.
     */
    public void createStudentAccounts(List<Student> students) throws RuntimeException {
        for(Student student : students) {
            String password = emailService.generateRandomCode();
            User optionalUser = userService.getUser(student.getEmail());
            if(optionalUser == null) {
                User user = new User(student.getEmail(), student.getStudentName(), PasswordHashingService.hashPassword(password), "student");
                String emailSubject = "Your account has been created!";
                String emailContent = generateContent(student.getEmail(), password);
                try {
                    emailService.sendEmail(student.getEmail(), emailSubject, emailContent);
                } catch (EmailException e) {
                    throw new RuntimeException(e);
                }
                userService.addUser(user);
            }
        }
    }

    /**
     * Creates submissions and adds them to the database, once files have been uploaded
     *
     * @param coordinator Coordinator responsible for the submission, extracted from the zip and Excel files,
     *                    generated by Brightspace and ProjectForum
     * @param user Database entity of user, saved in the database
     * @throws SQLException if the file could not be added to the database
     */
    public void createSubmissions(Coordinator coordinator, User user) throws SQLException {
        List<Association> associations = coordinator.getAssociations();
        for(Association association : associations) {
            Submission submission = association.getSubmission();
            Student student = association.getStudent();
            Blob file = new SerialBlob(submission.getSubmittedFile());
            SubmissionDB existingSubmission = submissionService.getSubmission(student.getEmail());
            if(existingSubmission == null) {
                Set<User> userSet = new HashSet<>();
                userSet.add(user);
                SubmissionDB submissionDB = new SubmissionDB(
                    student.getEmail(), file, coordinator.getEmail(), userSet, submission.getFileName(), null, null, false);
                submissionService.addSubmission(submissionDB);
            } else {
                existingSubmission.addUser(user);
                submissionService.updateSubmission(existingSubmission);
            }

        }
    }

    /**
     * Generates the content of the email, containing login credentials, according to some predefined format
     *
     * @param username username associated to account
     * @param password password associated to account
     * @return the generated email body
     */
    public String generateContent(String username, String password) {
        return "An administrator has uploaded theses on our annotation tool! We have" +
                " created an account for you. Here are the credentials that you can use to access your" +
                "account: <br> Username: <b>" + username + "</b> <br> Password: <b>" +
                password + "</b> <br> You can reset your password later on the platform!";
    }
}
