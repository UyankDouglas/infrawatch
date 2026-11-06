import { Link } from "react-router";
import { Compass } from "lucide-react";
import { EmptyState } from "@/components/EmptyState";
import { Button } from "@/components/ui/button";

export function NotFoundPage() {
  return (
    <EmptyState
      icon={Compass}
      title="Página não encontrada"
      description="O endereço acessado não existe no InfraWatch."
      action={
        <Button asChild variant="outline">
          <Link to="/">Voltar para a Visão Geral</Link>
        </Button>
      }
    />
  );
}
