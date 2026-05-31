import "@supabase/functions-js/edge-runtime.d.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.38.4"
import Stripe from "https://esm.sh/stripe@14.0.0?target=deno"

const stripe = new Stripe(Deno.env.get("STRIPE_SECRET_KEY") || "", {
  apiVersion: "2023-10-16",
  httpClient: Stripe.createFetchHttpClient(),
})

const supabase = createClient(
  Deno.env.get("SUPABASE_URL") || "",
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || ""
)

Deno.serve(async (req) => {
  try {
    const { userId, planType, priceId, successUrl, cancelUrl } = await req.json()

    if (!userId || !planType || !priceId) {
      return new Response(
        JSON.stringify({ error: "Missing required fields" }),
        { status: 400, headers: { "Content-Type": "application/json" } }
      )
    }

    const { data: user, error: userError } = await supabase
      .from("profiles")
      .select("email")
      .eq("id", userId)
      .single()

    if (userError || !user) {
      return new Response(
        JSON.stringify({ error: "User not found" }),
        { status: 404, headers: { "Content-Type": "application/json" } }
      )
    }

    const session = await stripe.checkout.sessions.create({
      mode: planType === "lifetime" ? "payment" : "subscription",
      payment_method_types: ["card"],
      line_items: [{ price: priceId, quantity: 1 }],
      success_url: successUrl || "https://profitdriving.com/success?session_id={CHECKOUT_SESSION_ID}",
      cancel_url: cancelUrl || "https://profitdriving.com/cancel",
      customer_email: user.email,
      client_reference_id: userId,
      metadata: {
        user_id: userId,
        plan_type: planType,
      },
    })

    await supabase.from("subscriptions").insert({
      user_id: userId,
      plan_type: planType,
      stripe_session_id: session.id,
      status: "pending",
      started_at: new Date().toISOString(),
    })

    return new Response(
      JSON.stringify({ url: session.url, sessionId: session.id }),
      { status: 200, headers: { "Content-Type": "application/json" } }
    )
  } catch (error) {
    console.error("Error creating checkout:", error)
    return new Response(
      JSON.stringify({ error: error.message }),
      { status: 500, headers: { "Content-Type": "application/json" } }
    )
  }
})
