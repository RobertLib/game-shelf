import { Button, Input, Select } from "../../../components/ui";
import { useSnackbar } from "../../../contexts/snackbar";
import { useState } from "react";
import apiFetch from "../../../utils/apiFetch";
import logger from "../../../utils/logger";
import PropTypes from "prop-types";

export default function UserForm({ onSubmit, user }) {
  const [data, setData] = useState({
    email: user?.email ?? "",
    password: "",
    passwordConfirm: "",
    role: user?.role ?? "USER",
  });

  const [isLoading, setIsLoading] = useState(false);

  const { enqueueSnackbar } = useSnackbar();

  const [errors, setErrors] = useState(null);

  const handleChange = ({ target: { name, value } }) => {
    setData((prevState) => ({ ...prevState, [name]: value }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();

    try {
      setIsLoading(true);
      setErrors(null);

      let response;

      if (user) {
        response = await apiFetch(`/api/admin/users/${user.id}`, {
          method: "PUT",
          body: JSON.stringify(data),
        });
      } else {
        response = await apiFetch("/api/admin/users", {
          method: "POST",
          body: JSON.stringify(data),
        });
      }

      const result = await response.json();

      if (!response.ok) {
        setErrors(result);
        return;
      }

      logger.info(`User ${user ? "updated" : "created"}:`, result);

      enqueueSnackbar(`User ${user ? "updated" : "created"} successfully.`);

      onSubmit?.();
    } catch (error) {
      logger.error("Error creating user:", error);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <form onSubmit={handleSubmit}>
      {errors?.errorMessage && (
        <div className="alert alert-danger" style={{ marginBottom: "1rem" }}>
          {errors.errorMessage}
        </div>
      )}

      <div className="col">
        <Input
          autoComplete="email"
          error={errors?.email}
          fullWidth
          label="Email"
          maxLength={255}
          name="email"
          onChange={handleChange}
          required
          type="email"
          value={data.email}
        />

        <Select
          error={errors?.role}
          fullWidth
          label="Role"
          name="role"
          onChange={handleChange}
          options={[
            { label: "User", value: "USER" },
            { label: "Admin", value: "ADMIN" },
          ]}
          required
          value={data.role}
        />

        {user ? null : (
          <>
            <Input
              autoComplete="new-password"
              error={errors?.password}
              fullWidth
              label="Password"
              maxLength={255}
              name="password"
              onChange={handleChange}
              required
              type="password"
              value={data.password}
            />

            <Input
              autoComplete="new-password"
              error={errors?.passwordConfirm}
              fullWidth
              label="Confirm Password"
              maxLength={255}
              name="passwordConfirm"
              onChange={handleChange}
              required
              type="password"
              value={data.passwordConfirm}
            />
          </>
        )}
      </div>

      <Button
        className="w-full"
        loading={isLoading}
        size="lg"
        style={{ marginTop: "1.5rem" }}
        type="submit"
      >
        {user ? "Update" : "Create"}
      </Button>
    </form>
  );
}

UserForm.propTypes = {
  onSubmit: PropTypes.func.isRequired,
  user: PropTypes.object,
};
