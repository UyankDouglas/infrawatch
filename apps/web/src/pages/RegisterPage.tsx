import { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { useAuth } from "@/features/auth/AuthProvider";
import { ApiError } from "@/services/api";
import { authApi } from "@/services/endpoints";

const schema = z
  .object({
    fullName: z.string().trim().min(2, "Informe seu nome completo."),
    organizationName: z.string().trim(),
    email: z.email("Informe um e-mail válido."),
    password: z.string().min(10, "A senha deve ter pelo menos 10 caracteres."),
    confirm: z.string(),
  })
  .refine((v) => v.password === v.confirm, { message: "As senhas não coincidem.", path: ["confirm"] });
const schemaWithOrganization = schema.refine((v) => v.organizationName.length >= 2, {
  message: "Informe o nome da organização.",
  path: ["organizationName"],
});
type FormValues = z.infer<typeof schema>;

export function RegisterPage() {
  const { setUser } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const inviteToken = params.get("convite") ?? "";
  const [error, setError] = useState<string | null>(null);
  const form = useForm<FormValues>({
    resolver: zodResolver(inviteToken ? schema : schemaWithOrganization),
    defaultValues: { fullName: "", organizationName: "", email: params.get("email") ?? "", password: "", confirm: "" },
  });

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null);
    try {
      const me = await authApi.register({
        fullName: values.fullName,
        email: values.email,
        password: values.password,
        organizationName: inviteToken ? undefined : values.organizationName,
        inviteToken: inviteToken || undefined,
      });
      setUser(me);
      navigate("/", { replace: true });
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) setError("Já existe uma conta com este e-mail.");
      else if (e instanceof ApiError && e.errors) {
        Object.entries(e.errors).forEach(([field, message]) => form.setError(field as keyof FormValues, { message }));
      } else setError("Não foi possível criar a conta. Tente novamente.");
    }
  });

  const field = (name: keyof FormValues, label: string, type = "text", autoComplete?: string) => (
    <div className="space-y-2">
      <Label htmlFor={name}>{label}</Label>
      <Input
        id={name}
        type={type}
        autoComplete={autoComplete}
        {...form.register(name)}
        aria-invalid={!!form.formState.errors[name]}
      />
      {form.formState.errors[name] && (
        <p className="text-xs text-destructive">{form.formState.errors[name]?.message}</p>
      )}
    </div>
  );

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Criar conta</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          {inviteToken
            ? "Você entrará na organização que o convidou com o perfil definido no convite."
            : "Você será o administrador da organização criada. Convide a equipe depois."}
        </p>
      </div>
      {error && (
        <Alert variant="destructive" role="alert">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}
      <form onSubmit={onSubmit} className="space-y-4" noValidate>
        {field("fullName", "Nome completo", "text", "name")}
        {!inviteToken && field("organizationName", "Nome da organização", "text", "organization")}
        {field("email", "E-mail", "email", "email")}
        {field("password", "Senha (mínimo 10 caracteres)", "password", "new-password")}
        {field("confirm", "Confirmar senha", "password", "new-password")}
        <Button type="submit" className="w-full" disabled={form.formState.isSubmitting}>
          {form.formState.isSubmitting && <Loader2 className="size-4 animate-spin" aria-hidden />}
          {inviteToken ? "Criar conta e aceitar convite" : "Criar conta e organização"}
        </Button>
      </form>
      <p className="text-center text-sm text-muted-foreground">
        Já tem conta?{" "}
        <Link to="/login" className="font-medium text-primary hover:underline">
          Entrar
        </Link>
      </p>
    </div>
  );
}
