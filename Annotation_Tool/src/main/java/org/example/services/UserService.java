package org.example.services;

import org.example.models.LoginRequest;
import org.example.models.User;
import org.example.database.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Streamable;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

@Service
public class UserService {
    private final UserRepository repo;

    /**
     * Constructor for the UserService
     *
     * @param repo the repository for the user
     *             database
     */
    @Autowired
    public UserService(UserRepository repo) {
        this.repo = repo;
    }

    /**
     * This method returns the user with the given id
     *
     * @param id the id of the user
     * @return the user with the given id
     */
    public User getUser(String id) {
        if ( repo.findById(id).isEmpty() ) {
            return null;
        }
        return repo.findById(id).get();
    }

    /**
     * This method returns all the users in the database
     *
     * @return a list of all the users in the database
     */
    public List<User> getUsers() {
        return Streamable.of(repo.findAll()).toList();
    }

    /**
     * This method adds a user to the database
     *
     * @param user the user to be added
     * @return the user that was added
     */
    public User addUser(User user) {
        // check if the user is null
        if (user == null) {
            return null;
        }
        // Check if the user already exists
        if (repo.findById(user.getId()).isPresent()) {
            return null;
        }
        return repo.save(user);
    }

    /**
     * This method updates a user in the database
     *
     * @param user the user to be updated
     * @return the user that was updated
     */
    public User updateUser(User user) {
        // check if the user is null
        if (user == null) {
            return null;
        }
        return repo.save(user);
    }

    /**
     * This method deletes a user from the database
     *
     * @param id the id of the user to be deleted
     * @return the user that was deleted
     */

    public User deleteUser(String id) {
        User deleted = repo.findById(id).orElse(null);
        if (repo.findById(id).isEmpty()) {
            return null;
        }
        repo.deleteById(id);
        return deleted;
    }

    /**
     * Method for registering a new user
     *
     * @param loginRequest a login request entity, that contains the username and the password
     */
    public void registerUser(LoginRequest loginRequest) {
        if (loginRequest == null) {
            return;
        }
        if (repo.findById(loginRequest.getUsername()).isPresent()) {
            return;
        }
        repo.save(new User(loginRequest.getUsername(), loginRequest.getPassword()));
    }

    /**
     * Method for authenticating a user
     *
     * @param loginRequest a login request entity, that contains the username and the password
     * @return true if the user is authenticated, false otherwise
     */
    public boolean authenticateUser(LoginRequest loginRequest) throws NoSuchAlgorithmException {
        System.out.println(loginRequest.getUsername());
        User user = repo.findById(loginRequest.getUsername()).orElse(null);
        if (user == null) {
            return false;
        }
        return user.getPassword().equals(hashPassword(loginRequest.getPassword()));
    }

    /**
     * Method for hashing a password
     *
     * @param password the password to be hashed
     * @return the hashed password
     * @throws NoSuchAlgorithmException if the hashing algorithm is not found
     */
    public String hashPassword(String password) throws NoSuchAlgorithmException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new NoSuchAlgorithmException("No such algorithm found");
        }
        md.update(password.getBytes());
        return new String(md.digest());
    }
}