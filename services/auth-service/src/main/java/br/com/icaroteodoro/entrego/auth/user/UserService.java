package br.com.icaroteodoro.entrego.auth.user;

import br.com.icaroteodoro.entrego.auth.role.Role;
import br.com.icaroteodoro.entrego.auth.role.RoleName;
import br.com.icaroteodoro.entrego.auth.role.RoleRepository;
import br.com.icaroteodoro.entrego.auth.user.dtos.CreateUserRequestDTO;
import br.com.icaroteodoro.entrego.auth.user.dtos.UserResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {
    public final UserRepository userRepository;
    public final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;

    public ResponseEntity<UserResponseDTO> create(CreateUserRequestDTO request) throws IllegalAccessException {
        if(userRepository.existsUserByEmail(request.email())) {
            throw new IllegalAccessException("Email already registered");
        }

        Role customerRole = roleRepository
                .findByName(RoleName.CUSTOMER)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Default CUSTOMER role not configured"
                        )
                );

        String encodedPassword =
                passwordEncoder.encode(request.password());

        User user = new User();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPassword(encodedPassword);
        user.addRole(customerRole);

        User saved = userRepository.save(user);

        return ResponseEntity.ok(new UserResponseDTO(saved.getId(),saved.getName(), saved.getEmail()));
    }

}
